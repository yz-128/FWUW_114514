import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.types.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.*;

/** Actual PNG bytes through the production generated resource pack, including colliding filenames. */
public final class VerifyReticleResources {
    static int checks,frames;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static int visible(ReticleResources.Skin skin)throws Exception {try(var in=skin.entry().open()){var image=ImageIO.read(in);int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>25)count++;return count;}}
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();var packs=new ArrayList<OfflinePack>();for(String arg:args){var p=new OfflinePack(Path.of(arg));p.readTypes();packs.add(p);}
        var optics=AttachmentType.attachments.stream().filter(a->a.lightStuffModel!=null&&!a.lightStuffModel.equalsIgnoreCase("none")
                ||a.blackMaskModel!=null&&!a.blackMaskModel.equalsIgnoreCase("none")).toList();
        try(var generated=GeneratedAssets.build()) {
            var ids=new HashSet<net.minecraft.resources.ResourceLocation>();
            for(var at:optics)for(int frame=0;frame<Math.max(1,at.lightTextureCount);frame++) {
                var source=ReticleResources.source(at,frame).orElseThrow();var id=Textures.forReticle(at,frame);
                var owner=packs.stream().filter(p->p.root.getFileName().toString().equals(at.contentPack)).findFirst().orElseThrow();
                var ownFile=owner.path(source.original());if(ownFile!=null)check(Arrays.equals(java.nio.file.Files.readAllBytes(ownFile),source.entry().readAllBytes()),at.shortName+" source is its own pack");
                check(ids.add(id),"distinct generated identifier for every item/frame: "+id);
                var supplier=generated.getResource(PackType.CLIENT_RESOURCES,id);check(supplier!=null,"generated resource exists: "+id);
                try(var stream=supplier.get()){check(Arrays.equals(source.entry().readAllBytes(),stream.readAllBytes()),"generated resource preserves original PNG bytes "+id);}frames++;
            }
        }
        var eft=AttachmentType.getAttachment("AimpointT1");var oct=AttachmentType.getAttachment("AimpointMicroT1");
        if(eft!=null&&oct!=null){
            var a=ReticleResources.source(eft,1).orElseThrow();var b=ReticleResources.source(oct,1).orElseThrow();
            check(a.original().equals(b.original()),"fixture really collides at DefaultDot1");
            check(visible(a)==0,"EFT frame 1 is authored off");check(visible(b)>0,"Octagon frame 1 is authored green dot");
            check(!Textures.forReticle(eft,1).equals(Textures.forReticle(oct,1)),"off frame cannot replace another pack's green dot");
            byte[] beforeA=a.entry().readAllBytes(),beforeB=b.entry().readAllBytes();
            var original=new LinkedHashMap<>(FlansMod.contentPacks);var entries=new ArrayList<>(original.entrySet());Collections.reverse(entries);FlansMod.contentPacks.clear();for(var e:entries)FlansMod.contentPacks.put(e.getKey(),e.getValue());
            ContentPackIndex.reload();Textures.clear();
            check(Arrays.equals(beforeA,ReticleResources.source(eft,1).orElseThrow().entry().readAllBytes()),"EFT PNG unaffected by reversed pack order/reload");
            check(Arrays.equals(beforeB,ReticleResources.source(oct,1).orElseThrow().entry().readAllBytes()),"Octagon PNG unaffected by reversed pack order/reload");
        }
        System.out.println("PASS: "+checks+" assertions, "+frames+" original reticle PNG frames through generated resources; owner isolation, off/green collision and reordered reload.");
    }
}
