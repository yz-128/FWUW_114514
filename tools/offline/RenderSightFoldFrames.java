import com.flansmod.client.SightFoldAnimations;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.network.GunSightAnimationPayload;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.util.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** CPU diagnostic frames using the real G33 endpoints and production hand/hinge math. No game. */
public final class RenderSightFoldFrames {
    static Path root;static GunSightAnimationPayload.Part change=new GunSightAnimationPayload.Part(new int[]{6,0,0,0},"G33Enabled","G33Disabled");
    static ModifiableType.DefaultAttachment node(String name,int slot){var n=new ModifiableType.DefaultAttachment();n.name=name;n.slot=slot;return n;}
    static BakedRig mesh(List<BakedRig.Quad> quads){var rig=new BakedRig();rig.quads.addAll(quads);return rig;}
    static boolean body(BakedRig rig,String key){return !rig.quadsByPart.containsKey("attachmentModel")||key.startsWith("attachmentModel#");}
    static void add(List<FPPreview.Part> parts,Matrix4f matrix,BakedRig rig,AttachmentType type,float alpha){
        if(alpha>0)parts.add(new FPPreview.Part(matrix,rig,FPPreview.skin(root,type.texture),0xFFFFFFFF,alpha,false,false));
    }
    static void nodes(List<FPPreview.Part> parts,ModifiableType owner,List<ModifiableType.DefaultAttachment> nodes,Matrix4f parent,int[] path,float phase){
        var indices=new HashMap<Integer,Integer>();
        for(var n:nodes){
            int[] next=Arrays.copyOf(path,path.length+2);next[path.length]=n.slot;next[path.length+1]=indices.merge(n.slot,1,Integer::sum)-1;
            var type=AttachmentType.getAttachment(n.name);var slot=owner.slots[n.slot];
            var m=new Matrix4f(parent).translate(slot.x+n.pos,slot.y,slot.z).rotateX(FmumActionPose.rad(slot.rotate));
            var rig=ModelCache.rigFor(type,type.modelScale);
            if(Arrays.equals(next,change.path())){
                var from=AttachmentType.getAttachment(change.from());var to=AttachmentType.getAttachment(change.to());
                var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
                if(phase>=1)add(parts,m,mesh(b.quadsByPart.get("attachmentModel")),to,1);
                else for(var t:SightFoldGeometry.plan(a,b).tracks()) {
                    boolean av=!t.source().isEmpty()&&body(a,t.sourceKey()),bv=!t.target().isEmpty()&&body(b,t.targetKey());
                    if(av)add(parts,new Matrix4f(m).mul(t.pose(phase)),mesh(t.source()),from,t.matched()&&bv?1:1-phase);
                    if(bv && (!t.matched()||!av))add(parts,new Matrix4f(m).mul(t.targetPose(phase)),mesh(t.target()),to,phase);
                    else if(bv&&t.textureChanged()) {
                        var quads=new ArrayList<BakedRig.Quad>();for(int i=0;i<t.source().size();i++){var q=t.source().get(i);quads.add(new BakedRig.Quad(q.positions(),t.target().get(i).uvs(),q.nx(),q.ny(),q.nz()));}
                        add(parts,new Matrix4f(m).mul(t.pose(phase)),mesh(quads),to,phase);
                    }
                }
            }else add(parts,m,mesh(rig.quadsByPart.getOrDefault("attachmentModel",rig.quads)),type,1);
            nodes(parts,type,n.children,m,next,phase);
        }
    }
    public static void main(String[]args)throws Exception {
        root=Path.of(args[0]);var output=Path.of(args[1]);Files.createDirectories(output);var pack=new OfflinePack(root);pack.readTypes();
        var gun=(GunType)pack.types.stream().filter(t->t instanceof GunType&&"AK74M".equals(t.shortName)).findFirst().orElseThrow();
        var tree=GunAttachments.copy(gun,gun.defaultAttachments);var rail=node("SVDLowSideMount",6);rail.children.add(node("G33Enabled",0));tree.get(6).add(rail);
        var before=gun.withAttachments(tree);before.selectedSightGroup=0;var finalTree=GunAttachments.copy(gun,tree);GunAttachments.nodeAt(finalTree,change.path()).name="G33Disabled";
        var after=gun.withAttachments(finalTree);var rig=ModelCache.rigFor(gun,gun.modelScale);var images=new ArrayList<BufferedImage>();
        for(float p:new float[]{0,.2F,.46F,.72F,1}){
            var frame=new SightFoldAnimations.Frame(p,List.of(change),before);float ads=(float)Math.pow(.66,p*GunSightState.FOLD_TICKS);
            var chain=new Matrix4f().translate(.56F,-.52F,-.72F);FmumFirstPersonPose.itemFrame(chain);
            FmumFirstPersonPose.placement(chain,rig.animation,ads,0,0);FmumFirstPersonPose.aim(chain,before,ads);
            SightFoldPose.weapon(chain,p);
            var parts=new ArrayList<FPPreview.Part>();parts.add(new FPPreview.Part(new Matrix4f(chain),rig,FPPreview.skin(root,gun.texture),0xFFFFFFFF));
            nodes(parts,after,after.defaultAttachments.stream().flatMap(List::stream).toList(),chain,new int[0],frame.phase());
            for(boolean left:new boolean[]{true,false}){
                var arm=ModelCache.rigForReference(left?gun.leftArmModel:gun.rightArmModel,1F);if(arm==null)continue;
                var pose=FmumReloadPose.arm(new Matrix4f(),rig.animation,left,GunReloadState.Frame.IDLE,GunActionState.Frame.IDLE,gun.modelScale,after);
                if(left)pose=SightFoldRenderer.arm(pose,frame,after);
                parts.add(new FPPreview.Part(new Matrix4f(chain).mul(pose),arm,null,left?0xFFFFC48F:0xFFD6AD87));
            }
            var file=output.resolve(String.format(Locale.ROOT,"fold-%03d.png",Math.round(p*100)));FPPreview.render(parts,file.toFile());images.add(ImageIO.read(file.toFile()));
        }
        var sheet=new BufferedImage(1500,830,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();g.setColor(new Color(18,25,35));g.fillRect(0,0,1500,830);g.setFont(new Font("SansSerif",Font.PLAIN,18));
        for(int i=0;i<images.size();i++){int x=i%3*500,y=i/3*415;g.drawImage(images.get(i),x,y+32,500,375,null);g.setColor(Color.WHITE);g.drawString(new String[]{"0% ready","20% reach","46% turn","72% release","100% return"}[i],x+10,y+24);}
        g.dispose();ImageIO.write(sheet,"png",output.resolve("fold-contact-sheet.png").toFile());
        System.out.println("CPU diagnostic frames generated. Flat diagnostic arm tint; not Minecraft screenshots.");
    }
}
