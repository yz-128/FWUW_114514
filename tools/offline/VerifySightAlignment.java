import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real AK74M ADS placement and authored side-rail adapters; no game or OpenGL. */
public final class VerifySightAlignment {
    private static int checks,failures;
    private static void check(boolean ok,String label){checks++;if(!ok){failures++;System.out.println("FAIL: "+label);}}
    private static ModifiableType.DefaultAttachment node(String name,int slot){
        var n=new ModifiableType.DefaultAttachment();n.name=name;n.slot=slot;return n;
    }
    private static GunType mounted(GunType ak,String optic){
        var tree=new ArrayList<List<ModifiableType.DefaultAttachment>>();
        for(var group:ak.defaultAttachments)tree.add(new ArrayList<>(group));
        while(tree.size()<ak.slots.length)tree.add(new ArrayList<>());
        if(optic.equals("PSO1"))tree.get(6).add(node(optic,6));
        else {
            var side=node("SVDLowSideMount",6);var lens=node(optic,0);
            if(optic.equals("NF7x")){var ring=node("NF34mm",0);ring.children.add(lens);side.children.add(ring);}
            else side.children.add(lens);
            tree.get(6).add(side);
        }
        return ak.withAttachments(tree);
    }
    private static Matrix4f gunPose(GunType view,ModelGeometry.Model body){
        Matrix4f pose=new Matrix4f().translate(.56F,-.52F,-.72F);
        FmumFirstPersonPose.itemFrame(pose);
        FmumFirstPersonPose.placement(pose,body,1,0,0);
        FmumFirstPersonPose.aim(pose,view,1);
        return pose;
    }
    /** Literal original parent: patched ItemRenderer:434, ForgeHooksClient:225, RenderGun:1007.
     * This expected transform intentionally does not call LegacyScopeProjection.base(). */
    private static Matrix4f originalParent(){
        float d=(float)(Math.PI/180);
        return new Matrix4f().translate(.56F,-.52F,-.72F).rotateY(45*d).scale(.4F)
                .translate(0,-.3F,0).scale(1.5F).rotateY(50*d).rotateZ(335*d)
                .translate(-.9375F,-.0625F,0).rotateZ(20*d).rotateY(-5*d)
                .translate(.15F,.375F,-1.005F).rotateX(.5F*d).rotateZ(5*d)
                .translate(0,.7F/160-.03F,-.35F/160);
    }
    private static final class Capture implements MultiBufferSource,VertexConsumer {
        final List<float[]> vertices=new ArrayList<>();float[] current;
        public VertexConsumer getBuffer(RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){current=new float[]{x,y,z,0,0};vertices.add(current);return this;}
        public VertexConsumer setUv(float u,float v){current[3]=u;current[4]=v;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static void glassContract(BakedRig glass,Matrix4f lens,String label){
        var pose=new PoseStack();pose.mulPose(lens);
        for(boolean mirrored:new boolean[]{false,true}){
            var capture=new Capture();
            var projection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,.005F,100);
            FmumSightRenderer.renderLensSurface(pose,capture,glass,new Matrix4f(projection).mul(lens),.37F,mirrored);
            check(capture.vertices.size()==glass.quads.size()*4,label+" native scopeGlassModel.render submits every authored face");
            int cursor=0;
            for(var quad:glass.quads)for(int j=0;j<4;j++){
                int index=mirrored?3-j:j;var actual=capture.vertices.get(cursor++);
                var expected=lens.transformPosition(new Vector3f(quad.positions()[index]));
                check(Math.abs(actual[0]-expected.x)<1E-5&&Math.abs(actual[1]-expected.y)<1E-5
                        &&Math.abs(actual[2]-expected.z)<1E-5,label+" keeps original physical glass vertex");
                var expectedUv=FmumSightRenderer.framebufferUv(glass,quad.uvs()[index][0],quad.uvs()[index][1],mirrored);
                check(Math.abs(actual[3]-expectedUv[0])<1E-5F&&Math.abs(actual[4]-expectedUv[1])<1E-5F,
                        label+" maps authored UV axes to the framebuffer without removing faces");
            }
        }
    }
    private static void optic(GunType ak,ModelGeometry.Model body,String name){
        var view=mounted(ak,name);var sight=GunSightState.scopeSight(view);
        check(sight!=null && sight.type().shortName.equals(name),"real ADS selects "+name);
        var rig=ModelCache.rigFor(sight.type(),sight.type().modelScale);var model=rig.animation;
        Vector3f mount=ScopeReticleRenderer.mount(sight);
        Matrix4f toEye=gunPose(view,body).translate(mount).rotateX((float)Math.toRadians(sight.roll()-sight.type().aimCenterX));
        Vector3f objective=toEye.transformPosition(FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0));
        // RenderGun:780-792 uses tracked coordinates before the GL parent and fixed eye,
        // not camera-space slopes or a second subtraction of the sight's bore angle.
        var trackedObjective=originalParent().invert().mul(toEye).transformPosition(
                FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0));
        trackedObjective.sub(-81.5F/160,.4F,0);
        check(trackedObjective.x>0,"tracked objective is ahead of native eye "+name);
        float dy=-trackedObjective.y/(float)Math.hypot(trackedObjective.x,trackedObjective.z)
                *FmumActionPose.scalar(model,"scopeMaskMultTransY",.4F);
        float dz=-trackedObjective.z/trackedObjective.x*FmumActionPose.scalar(model,"scopeMaskMultTransZ",.4F);
        float[] divisors=model.fmumArrays.getOrDefault("scopeMaskAlphaDivisor",new float[]{.00003F});
        float divisor=divisors.length==0?.00003F:divisors[0];
        float brightness=Math.clamp(1-(dy*dy+dz*dz)/Math.max(1e-9F,divisor),0,1);
        float minimum=FmumActionPose.scalar(model,"minAlphaToRenderScopeTex",.05F);
        var picture=ScopeEyeRelief.picture(model,sight.type().modelScale,toEye,0);
        check(Math.abs(picture.alpha()-brightness)<1E-5F,"production off-axis fade matches independent FMUM formula "+name);
        check(picture.visible(),"production eye relief keeps aligned "+name+" visible");
        System.out.printf(Locale.ROOT,"%s actual mount=%s objectiveEye=(%.8f,%.8f,%.8f) mask=(%.8f,%.8f) divisor=%.8f brightness=%.8f threshold=%.8f%n",
                name,mount,objective.x,objective.y,objective.z,dy,dz,divisor,brightness,minimum);
        check(objective.isFinite() && objective.z<0,"actual objective projects in front of the eye "+name);
        check(brightness>=minimum && brightness>=.05F,"complete ADS optical picture remains visible "+name);
        Vector3f pupil=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(sight.type().modelScale);
        Vector3f scale=FmumSightPicture.vector(model,"scopeGlassModelScale",1,1,1).mul(sight.type().modelScale);
        Matrix4f lens=new Matrix4f(toEye).translate(pupil).scale(scale);
        var glass=ModelCache.rigForReference(sight.type().scopeGlassModel,1F);
        var face=glass.quads.stream().filter(q->q.nx()<-.9F).findFirst().orElseThrow();
        float u=face.uvs()[0][0],v=face.uvs()[0][1];
        Vector3f p=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,u,v));
        Vector3f right=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,u+.01F,v)).sub(p);
        Vector3f down=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,u,v+.01F)).sub(p);
        System.out.printf(Locale.ROOT,"%s real glass UV-to-eye basis dX/dU=%.8f dY/dV=%.8f%n",name,right.x/.01F,down.y/.01F);
        // How much of the screen the ocular covers decides whether magnifying the world pass needs
        // a surround at all: if the ocular's angular radius exceeds the magnified half-FOV, the
        // scope picture already fills the screen, exactly as a real scope does.
        float pupilDistance=FmumSightPicture.pupilDistance(model,sight.type().modelScale,new Vector3f());
        float ocularAngle=0F;
        for(var quad:glass.quads)for(int i=0;i<quad.positions().length;i++) {
            float[] pos=quad.positions()[i];
            Vector3f e=lens.transformPosition(new Vector3f(pos[0],pos[1],pos[2]));
            if(e.z>=0)continue;
            ocularAngle=Math.max(ocularAngle,(float)Math.toDegrees(Math.atan(Math.hypot(e.y,e.x)/-e.z)));
        }
        for(int step=0;step<Math.max(1,sight.type().fovZoomLevels.length);step++) {
            double zoom=ScopeMagnification.zoom(sight.type(),step);
            double half=Math.toDegrees(Math.atan(Math.tan(Math.toRadians(70)/2)/zoom));
            System.out.printf(Locale.ROOT,
                    "%s step=%d zoom=%.3f ocularHalfAngle=%.3f deg magnifiedHalfFov=%.3f deg coversScreen=%s%n",
                    name,step,zoom,ocularAngle,half,ocularAngle>=half);
        }
        // The port's camera and this authored mesh have different horizontal conventions.
        // Keep this evidence; raw UV preservation alone failed to validate world direction.
        check(right.x<0 && down.y>0,"real PSO1Glass retains authored double-reversed UV evidence "+name);
        glassContract(glass,lens,name);
    }
    public static void main(String[]args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
        var ak=(GunType)InfoType.infoTypes.stream().filter(t->t instanceof GunType && "AK74M".equals(t.shortName)).findFirst().orElseThrow();
        var body=ModelCache.load(ak);check(body!=null && FmumFirstPersonPose.hasPose(body),"actual AK74M authored pose loaded");
        for(String optic:List.of("PSO1","NF7x","SeecterDR1x4x","G33Enabled"))optic(ak,body,optic);
        // A cap/side with wrapping UVs must not disappear or be clamped by vertex emission.
        var wrapping=new BakedRig();
        wrapping.quads.add(new BakedRig.Quad(new float[][]{{0,0,0},{0,0,1},{0,1,1},{0,1,0}},
                new float[][]{{-1,-2},{2,-2},{2,3},{-1,3}},1,0,0));
        glassContract(wrapping,new Matrix4f().translate(.2F,.3F,-1),"authored back face with wrapping UVs");
        if(failures>0)throw new AssertionError(failures+" of "+checks+" optical alignment checks failed");
        System.out.println("PASS: "+checks+" assertions; real AK74M full ADS, side-rail/ring mounts, independent native mask formula and production oriented-UV/full-glass emission. Offline only; no game/OpenGL executed.");
    }
}
