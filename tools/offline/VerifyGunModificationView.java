import com.flansmod.client.gui.GunModificationView;
import com.flansmod.client.gui.GunModificationPreview;
import com.flansmod.client.model.loader.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import org.joml.*;
import java.util.*;
import java.util.List;
import java.nio.file.Path;

/** Real assembly bounds and actual GUI vertex emission, with no OpenGL or game launch. */
public final class VerifyGunModificationView {
    private static int checks;
    static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    static void collect(List<Vector3f> points,BakedRig rig,Matrix4f transform) {
        if(rig==null || rig.quads.isEmpty())return;
        for(int i=0;i<8;i++)points.add(transform.transformPosition(new Vector3f(
                (i&1)==0?rig.minX:rig.maxX,(i&2)==0?rig.minY:rig.maxY,(i&4)==0?rig.minZ:rig.maxZ)));
    }
    static void nodes(List<Vector3f> points,ModifiableType owner,List<ModifiableType.DefaultAttachment> nodes,Matrix4f pose) {
        for(var node:nodes) {
            var type=AttachmentType.getAttachment(node.name);if(type==null || node.slot<0 || node.slot>=owner.slots.length)continue;
            var slot=owner.slots[node.slot];var transform=new Matrix4f(pose).translate(slot.x+node.pos,slot.y,slot.z).rotateX((float)java.lang.Math.toRadians(slot.rotate));
            collect(points,ModelCache.rigFor(type,type.modelScale),transform);nodes(points,type,node.children,transform);
        }
    }
    static ModifiableType.AttachableSlot mount(float x,float y,float z,float rotation) {
        var slot=new ModifiableType.AttachableSlot();slot.x=x;slot.y=y;slot.z=z;slot.rotate=rotation;return slot;
    }
    static void checkMountProjection(GunModificationView.Frame frame) {
        var gun=new GunType(new TypeFile(EnumType.gun,"projection-fixture",false));
        var outer=new AttachmentType(new TypeFile(EnumType.attachment,"projection-outer",false));
        var inner=new AttachmentType(new TypeFile(EnumType.attachment,"projection-inner",false));
        outer.shortName="__projection_outer";inner.shortName="__projection_inner";
        try {
            // Deliberately unequal mesh scales must not scale block-space mount coordinates.
            gun.modelScale=.2F;outer.modelScale=4F;inner.modelScale=.07F;
            gun.slots=new ModifiableType.AttachableSlot[]{mount(.2F,.1F,-.05F,90)};
            outer.slots=new ModifiableType.AttachableSlot[]{mount(-.4F,.25F,.3F,-45)};
            inner.slots=new ModifiableType.AttachableSlot[]{mount(.15F,-.12F,.07F,0)};
            var unrelated=new ModifiableType.DefaultAttachment();unrelated.name=outer.shortName;unrelated.slot=0;unrelated.pos=-.8F;
            var parent=new ModifiableType.DefaultAttachment();parent.name=outer.shortName;parent.slot=0;parent.pos=.31F;
            var child=new ModifiableType.DefaultAttachment();child.name=inner.shortName;child.slot=0;child.pos=-.17F;parent.children.add(child);
            List<List<ModifiableType.DefaultAttachment>> tree=List.of(List.of(unrelated,parent));
            int[] path={0,1,0,0};var unchanged=new Matrix4f(frame.matrix());
            for(float rail:new float[]{0,.09F,.27F}) {
                // Independent scalar rotations: inner mount -> -45 degree parent -> 90 degree root.
                float c=(float)java.lang.Math.sqrt(.5),localY=-.12F,localZ=.07F;
                float y=c*localY+c*localZ+.25F,z=-c*localY+c*localZ+.3F;
                var expected=new Vector3f(.15F+rail-.4F-.17F+.2F+.31F,-z+.1F,y-.05F);
                frame.matrix().transformPosition(expected);
                var screen=GunModificationView.projectMount(frame,gun,tree,path,0,rail);
                check(screen!=null && screen.distance(expected)<.0001F,"actual GUI frame projects nested rolled mount with rail offset and sibling identity");
            }
            var root=GunModificationView.projectMount(frame,gun,tree,new int[0],0,.09F);
            var expectedRoot=frame.matrix().transformPosition(new Vector3f(.29F,.1F,-.05F));
            check(root!=null && root.distance(expectedRoot)<.0001F,"root interface uses identity parent without applying its own roll to its origin");
            check(frame.matrix().equals(unchanged),"projecting nodes preserves the current preview frame");
            check(Arrays.equals(path,new int[]{0,1,0,0}) && parent.pos==.31F && child.pos==-.17F,"projecting nodes preserves real assembly and selection path");
            check(GunModificationView.projectMount(frame,gun,tree,new int[]{0},0,0)==null,"odd mount path is rejected");
            check(GunModificationView.projectMount(frame,gun,tree,new int[]{0,2},0,0)==null,"missing sibling is rejected");
            check(GunModificationView.projectMount(frame,gun,tree,path,1,0)==null,"missing nested slot is rejected");
            check(GunModificationView.projectMount(frame,gun,tree,path,0,Float.NaN)==null,"nonfinite rail is rejected");
            inner.slots[0].y=Float.POSITIVE_INFINITY;
            check(GunModificationView.projectMount(frame,gun,tree,path,0,0)==null,"nonfinite authored mount is rejected");
        } finally {
            AttachmentType.attachments.remove(outer);AttachmentType.attachments.remove(inner);
            InfoType.infoTypes.remove(gun);InfoType.infoTypes.remove(outer);InfoType.infoTypes.remove(inner);
        }
    }
    public static void main(String[] args)throws Exception {
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();int guns=0;
        GunModificationView.Frame projectionFrame=null;
        for(var type:pack.types)if(type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG && !gun.magazineNames.isEmpty()) {
            var rig=ModelCache.rigFor(gun,gun.modelScale);if(rig==null || rig.quads.isEmpty())continue;
            var points=new ArrayList<Vector3f>();collect(points,rig,new Matrix4f());
            for(var group:gun.defaultAttachments)nodes(points,gun,group,new Matrix4f());
            for(int[] size:new int[][]{{180,100},{300,160},{510,325}})for(float[] angle:new float[][]{{-24,0},{0,0},{75,40},{160,-55}}) {
                int x=17,y=23,w=size[0],h=size[1];
                var frame=GunModificationView.frame(points,angle[0],angle[1],1F,x,y,w,h);
                check(frame!=null && Float.isFinite(frame.scale()) && frame.scale()>0,"finite framing "+gun.shortName);
                if(projectionFrame==null)projectionFrame=frame;
                float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
                for(var point:points) {
                    var screen=frame.matrix().transformPosition(new Vector3f(point));
                    check(screen.x>=x-0.01F && screen.x<=x+w+.01F && screen.y>=y-.01F && screen.y<=y+h+.01F,"complete assembly inside viewport "+gun.shortName);
                    minX=java.lang.Math.min(minX,screen.x);maxX=java.lang.Math.max(maxX,screen.x);
                    minY=java.lang.Math.min(minY,screen.y);maxY=java.lang.Math.max(maxY,screen.y);
                }
                check(java.lang.Math.abs((minX+maxX)/2F-(x+w/2F))<.01F && java.lang.Math.abs((minY+maxY)/2F-(y+h/2F))<.01F,"centred silhouette "+gun.shortName);
                if("VPO101".equals(gun.shortName) && angle[0]==-24 && angle[1]==0 && w==510) {
                    float full=java.lang.Math.max(1F,frame.projectedMax().distance(frame.projectedMin()));
                    float oldScale=java.lang.Math.min(w*.88F,h*.83F)/full;
                    check(frame.scale()>oldScale*1.2F,"long rifle framing uses width rather than height-sized sphere");
                    check(maxX-minX>=w*.85F,"VPO101 fills the default viewport horizontally");
                    System.out.printf(java.util.Locale.ROOT,"VPO101 projected width %.1f/510; old sphere scale %.2f, fitted scale %.2f%n",maxX-minX,oldScale,frame.scale());
                }
            }
            guns++;
        }
        check(guns==31,"31 EFT firearm assemblies checked");
        checkMountProjection(projectionFrame);
        var rig=new BakedRig();rig.quads.add(new BakedRig.Quad(new float[][]{{0,0,0},{1,0,0},{1,1,0},{0,1,0}},
                new float[][]{{0,0},{1,0},{1,1},{0,1}},0,0,1));
        var pose=new PoseStack();pose.scale(100,-100,100);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45));
        var recorder=new Recorder();var method=GunModificationPreview.class.getDeclaredMethod("renderMesh",PoseStack.class,VertexConsumer.class,BakedRig.class,boolean.class);method.setAccessible(true);method.invoke(null,pose,recorder,rig,false);
        check(recorder.positions.size()==4,"actual preview emits all quad vertices");
        var expected=pose.last().normal().transform(new Vector3f(0,0,1)).normalize();
        check(recorder.normal.distance(expected)<.00001F && java.lang.Math.abs(recorder.normal.x)>.1F,"actual GUI normals follow rotated model pose");
        check(recorder.lowColor>=178 && recorder.highColor<=255,"studio lighting preserves readable authored colour");
        for(Vector3f n:List.of(new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1),new Vector3f(-1,-2,-3))) {
            float shade=GunModificationView.shade(n);check(shade>=.70F && shade<=1.00001F,"bounded studio face shading");
        }
        System.out.println("PASS: "+checks+" assertions; 31 complete EFT assemblies, 3 viewport sizes, 4 rotations, VPO101 sizing, centering, actual transformed vertex normals, studio lighting and nested mount/rail projection. No OpenGL/game executed.");
    }
    static final class Recorder implements VertexConsumer {
        final List<Vector3f> positions=new ArrayList<>();Vector3f normal;int lowColor=255,highColor=0;
        public VertexConsumer addVertex(float x,float y,float z){positions.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){lowColor=java.lang.Math.min(lowColor,java.lang.Math.min(r,java.lang.Math.min(g,b)));highColor=java.lang.Math.max(highColor,java.lang.Math.max(r,java.lang.Math.max(g,b)));return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){normal=new Vector3f(x,y,z);return this;}
    }
}
