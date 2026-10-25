import com.flansmod.common.GunAttachments;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;

/**
 * The attachment tooltip, against real content.
 *
 * <p>1.7.10's ItemAttachment printed the pack name in italics and the definition's
 * {@code Description}, split on '_' into one line per segment. The port used to print a computed
 * list of attachment parents instead, which read as nonsense ("a muzzle brake fits onto a dust
 * cover"); the line's label promises weapons, so it lists the guns whose slots accept the part.
 *
 * <p>Args: {@code <final jar> <EFT pack> [more packs...]}.
 */
public final class VerifyAttachmentTooltip {
    static int checks;
    static java.util.zip.ZipFile JAR;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static ClassNode node(String name) throws Exception {
        var entry=JAR.getEntry(name+".class");
        check(entry!=null,"final JAR class exists: "+name);
        var n=new ClassNode();
        try(var in=JAR.getInputStream(entry)) { new ClassReader(in.readAllBytes()).accept(n,0); }
        return n;
    }
    static boolean calls(ClassNode n,String method,String ownerSuffix,String called) {
        for(var m:n.methods)if(m.name.equals(method))for(var i:m.instructions)
            if(i instanceof MethodInsnNode c && c.owner.endsWith(ownerSuffix) && c.name.equals(called))return true;
        return false;
    }
    static boolean hasString(ClassNode n,String fragment) {
        for(var m:n.methods)for(var i:m.instructions) {
            if(i instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains(fragment))return true;
            if(i instanceof InvokeDynamicInsnNode indy)
                for(var arg:indy.bsmArgs)
                    if(arg instanceof String s && s.contains(fragment))return true;
        }
        return false;
    }
    static AttachmentType attachment(String shortName) {
        for(var type:InfoType.infoTypes)
            if(type instanceof AttachmentType at && shortName.equals(at.shortName))return at;
        throw new AssertionError("missing attachment definition "+shortName);
    }
    static List<String> gunNames(AttachmentType part) {
        var names=new ArrayList<String>();
        for(GunType gun:GunAttachments.gunsAccepting(part))names.add(gun.shortName);
        return names;
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JAR=new java.util.zip.ZipFile(args[0]);
        for(int i=1;i<args.length;i++)new OfflinePack(Path.of(args[i])).readTypes();

        // ---------------------------------------------------------------- the muzzle brake
        var brake=attachment("6P26AKS74UMuzzleBrake");
        check("Need:AK74 AK74N AK74M AKS774U".equals(brake.description),
                "the pack's Description line is parsed and kept (" + brake.description + ")");
        check("akmuzzlebrake".equals(brake.attachmentType),"its interface is akmuzzlebrake");

        var guns=gunNames(brake);
        check(guns.equals(List.of("AK74","AK74M","AK74N","AKS74U","RPK16")),
                "the tooltip lists exactly the rifles that accept it: "+guns);
        check(!guns.contains("220mmMosinBarrel") && !guns.contains("6P26AKS74UDustCover"),
                "the old attachment-parent list does not leak into the weapon list");
        check(GunAttachments.gunsAccepting(brake).stream().allMatch(g->g instanceof GunType),
                "only guns are listed");

        // ---------------------------------------------------------------- a scope and the general rule
        var scope=attachment("EOTech553");
        var scopeGuns=gunNames(scope);
        check(!scopeGuns.isEmpty(),"a sight also reports its host weapons: "+scopeGuns);
        check(scopeGuns.stream().noneMatch(n->n.contains("MosinBarrel")),"no parts leak into the sight list");

        int total=0, withGuns=0;
        for(var type:InfoType.infoTypes) if(type instanceof AttachmentType part) {
            total++;
            if(!GunAttachments.gunsAccepting(part).isEmpty()) withGuns++;
        }
        check(total>100,"the packs really carry attachments ("+total+")");
        check(withGuns>total/2,"most attachments resolve at least one host weapon ("+withGuns+"/"+total+")");

        // ---------------------------------------------------------------- wiring
        var item=node("com/flansmod/common/types/ItemFlan");
        check(calls(item,"appendHoverText","common/GunAttachments","gunsAccepting"),
                "the tooltip asks which guns accept the part");
        check(hasString(item,"attachment.flansmod.parents"),"the tooltip still uses the fits-onto line");
        check(!calls(item,"appendHoverText","GunAttachmentCatalog","directParents"),
                "the attachment-parent list is gone from the tooltip");
        check(calls(item,"appendHoverText","Component","literal") || hasString(item,"description"),
                "the tooltip prints the pack's Description");
        var attachments=node("com/flansmod/common/GunAttachments");
        check(attachments.methods.stream().anyMatch(m->m.name.equals("gunsAccepting")),
                "GunAttachments owns the host-weapon query");

        System.out.println("PASS VerifyAttachmentTooltip: "+checks+" assertions; real pack Description, host-weapon"
                +" list for the muzzle brake and a sight, no attachment parents, and final-JAR wiring. No game/GPU/input.");
    }
}
