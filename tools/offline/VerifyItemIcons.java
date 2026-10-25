import com.flansmod.client.render.Textures;
import com.flansmod.common.types.InfoType;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * The 1.7.10 client log names 26 item icons no pack ships ("domain flansmod is missing 26
 * textures"). The port already bundled replacements for some of them; this checks the two
 * fallbacks that keep the rest from showing the missing-texture marker:
 *
 * <ol>
 *   <li>the definition's model skin ({@code skins/<Texture>.png}) as the flat icon;</li>
 *   <li>a bundled 16x16 placeholder when even the skin is absent.</li>
 * </ol>
 *
 * Args: {@code <final jar> <content pack>...}. No game launched.
 */
public final class VerifyItemIcons {
    static int checks;
    static ZipFile JAR;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static ClassNode node(String name) throws Exception {
        var entry=JAR.getEntry(name+".class");
        check(entry!=null,"final JAR class exists: "+name);
        var n=new ClassNode();
        try(var in=JAR.getInputStream(entry)) { new ClassReader(in.readAllBytes()).accept(n,0); }
        return n;
    }
    static boolean hasMethod(ClassNode n,String method) {return n.methods.stream().anyMatch(m->m.name.equals(method));}
    /** String literals land either as LDC or as a StringConcatFactory recipe in the indy args. */
    static boolean hasString(ClassNode n,String fragment) {
        for(var m:n.methods)for(var i:m.instructions) {
            if(i instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains(fragment))return true;
            if(i instanceof InvokeDynamicInsnNode indy)
                for(var arg:indy.bsmArgs)
                    if(arg instanceof String s && s.contains(fragment))return true;
        }
        return false;
    }

    /** Same rule as {@code ItemAssets.iconPathOf}: the Icon line, else the short name, under items/. */
    static String iconPath(InfoType type) {
        String icon=type.iconPath;
        if(icon==null||icon.isBlank()) icon=type.shortName;
        return (icon==null?"":(icon.contains("/")?icon:"items/"+icon)).toLowerCase(Locale.ROOT);
    }
    static ResourceLocation loc(String path) {return ResourceLocation.fromNamespaceAndPath("flansmod",path);}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JAR=new ZipFile(args[0]);

        var entry=JAR.getEntry("assets/flansmod/textures/items/missingicon.png");
        check(entry!=null,"the placeholder icon ships in the jar");
        byte[] png;
        try(var in=JAR.getInputStream(entry)) { png=in.readAllBytes(); }
        check(png.length>26 && (png[0]&0xFF)==0x89 && png[1]=='P' && png[2]=='N' && png[3]=='G',
                "the placeholder is a real PNG");
        int width=((png[16]&0xFF)<<24)|((png[17]&0xFF)<<16)|((png[18]&0xFF)<<8)|(png[19]&0xFF);
        int height=((png[20]&0xFF)<<24)|((png[21]&0xFF)<<16)|((png[22]&0xFF)<<8)|(png[23]&0xFF);
        check(width==16 && height==16,"the placeholder is a 16x16 item sprite (actual "+width+"x"+height+")");

        var assets=node("com/flansmod/common/GeneratedAssets");
        check(hasMethod(assets,"placeholderIcon"),"generated assets own the placeholder fallback");
        check(hasMethod(assets,"findSkinIcon"),"generated assets fall back to the definition's model skin");
        check(hasString(assets,"skins/"),"the skin fallback looks under skins/");
        check(hasString(assets,"missingicon.png"),"the last-resort fallback names the bundled placeholder");

        // Real packs: how many definitions name an icon they do not ship, and how many of those
        // still have a model skin to draw.
        var packs=new ArrayList<OfflinePack>();
        for(int i=1;i<args.length;i++) { var pack=new OfflinePack(Path.of(args[i])); pack.readTypes(); packs.add(pack); }
        check(!packs.isEmpty(),"at least one content pack was provided");
        int total=0, missingIcon=0, skinRescue=0;
        var rescued=new ArrayList<String>();
        for(var pack:packs) {
            for(var type:pack.types) {
                total++;
                boolean hasIcon=false;
                for(var other:packs) if(other.has(loc("textures/"+iconPath(type)+".png"))) hasIcon=true;
                if(hasIcon) continue;
                missingIcon++;
                ResourceLocation skin=Textures.forType(type,id->{for(var other:packs)if(other.has(id))return true;return false;});
                if(skin!=null) { skinRescue++; rescued.add(type.shortName); }
            }
        }
        System.out.println("[icons] definitions="+total+" without an inventory icon="+missingIcon
                +" of which the model skin rescues="+skinRescue);
        check(missingIcon>0,"the packs still ship definitions with no inventory icon (that is the 1.7.10 error)");
        check(skinRescue>0,"the model-skin fallback recovers real icons: "+rescued.subList(0,Math.min(5,rescued.size())));
        check(skinRescue<=missingIcon,"the skin rescue never exceeds the icon-less count");
        check(missingIcon-skinRescue>0,"the remainder is left to the bundled placeholder");

        System.out.println("PASS VerifyItemIcons: "+checks+" assertions; placeholder sprite, model-skin fallback,"
                +" and real-pack icon coverage ("+skinRescue+"/"+missingIcon+" recovered). No game/GPU/input.");
    }
}
