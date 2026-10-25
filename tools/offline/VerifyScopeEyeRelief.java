import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Path;

/** Production pupil shading, real lens-strip emission and malformed/off-axis optical poses. */
public final class VerifyScopeEyeRelief {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void near(float value,float expected,String label){check(Float.isFinite(value)&&Math.abs(value-expected)<1E-5F,label);}
    private static final class Recorder implements MultiBufferSource,VertexConsumer {
        int vertices,clear,opaque;float minY,maxY,minZ,maxZ;
        Recorder(BakedRig glass){minY=glass.minY;maxY=glass.maxY;minZ=glass.minZ;maxZ=glass.maxZ;}
        public VertexConsumer getBuffer(RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){
            check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite physical shadow vertex");
            check(y>=minY-1E-4F&&y<=maxY+1E-4F&&z>=minZ-1E-4F&&z<=maxZ+1E-4F,"shadow stays on lens bounds");vertices++;return this;
        }
        public VertexConsumer setColor(int r,int g,int b,int a){
            check(r==255&&g==255&&b==255&&a==255,"authored mask colours come from the original PNG");
            if(a==0)clear++;if(a==255)opaque++;return this;
        }
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){check(Float.isFinite(x+y+z),"finite lens normal");return this;}
    }
    public static void main(String[]args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String arg:args)new OfflinePack(Path.of(arg)).readTypes();
        var at=AttachmentType.getAttachment("PSO1");var model=ModelCache.load(at);
        var objective=FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0);
        // RenderGun measures the objective against its fixed pre-parent eye. A bare
        // Ry(90) camera omits the ItemRenderer/Forge parent and is not an aligned native pose.
        var tracked=new Matrix4f().translation(LegacyScopeProjection.EYE_X+.5F-objective.x,
                LegacyScopeProjection.EYE_Y-objective.y,-objective.z);
        var aligned=LegacyScopeProjection.base().mul(tracked);
        var picture=ScopeEyeRelief.picture(model,at.modelScale,aligned,0);
        check(picture.visible(),"real PSO-1 aligned picture remains visible");near(picture.alpha(),1,"aligned scope brightness");
        near(picture.shadow(0,0),0,"clear centre");near(picture.shadow(1.1F,0),1,"dark edge");
        for(int y=-10;y<=10;y++)for(int z=-10;z<=10;z++) {
            float shade=picture.shadow(picture.shiftY()+y/10F,picture.shiftZ()+z/10F);check(Float.isFinite(shade)&&shade>=0&&shade<=1,"continuous bounded pupil shadow");
            near(shade,picture.shadow(picture.shiftY()-y/10F,picture.shiftZ()-z/10F),"shadow symmetry about the measured pupil centre");
        }
        var shifted=LegacyScopeProjection.base().mul(new Matrix4f(tracked).translate(0,.01F,0));
        var offAxis=ScopeEyeRelief.picture(model,at.modelScale,shifted,0);
        check(offAxis.alpha()<picture.alpha(),"small off-axis movement dims scene");
        check(offAxis.shiftY()!=picture.shiftY() || offAxis.shiftZ()!=picture.shiftZ(),"shadow tracks eye movement");
        var outside=LegacyScopeProjection.base().mul(new Matrix4f(tracked).translate(0,1,0));
        check(!ScopeEyeRelief.picture(model,at.modelScale,outside,0).visible(),"outside eye box closes picture");
        check(!ScopeEyeRelief.picture(model,at.modelScale,LegacyScopeProjection.base().translate(-2,0,0),0).visible(),"behind-camera optic closes picture");
        check(!ScopeEyeRelief.picture(model,at.modelScale,new Matrix4f().zero(),0).visible(),"singular transform closes picture");
        check(!ScopeEyeRelief.picture(null,at.modelScale,aligned,0).visible(),"missing model closes picture");
        var distant=LegacyScopeProjection.base().translate(LegacyScopeProjection.EYE_X+2-objective.x,
                LegacyScopeProjection.EYE_Y-objective.y,-objective.z);
        check(ScopeEyeRelief.picture(model,at.modelScale,distant,0).radius()<picture.radius(),"eye too far reduces exit pupil");
        var glass=ModelCache.rigForReference(at.scopeGlassModel,1F);
        for(boolean mirrored:new boolean[]{false,true}) {
            var recorder=new Recorder(glass);ScopeMaskRenderer.render(new PoseStack(),recorder,ModelCache.rigFor(at,at.modelScale),at,glass,aligned,0,0,70,0,mirrored);
            check(recorder.vertices>0&&recorder.vertices%4==0,"complete real lens-strip shadow geometry");
            check(recorder.opaque>0,"original mask texture geometry emitted");
        }
        // Eye-space scaling leaves the inverse camera origin and therefore the native
        // pupil-distance scale/FOV unchanged. Its different parent is not used as a claim
        // about mask offsets; those are measured in the fixed original tracking frame.
        int nativeMasks=0;
        for(var optic:AttachmentType.attachments)if(hasScope(optic)) {
            var rig=ModelCache.rigFor(optic,optic.modelScale);var lens=ModelCache.rigForReference(optic.scopeGlassModel,1F);
            var lensCenter=FmumSightPicture.vector(rig.animation,"scopeObjectiveLensCenter",0,0,0);
            var toEye=LegacyScopeProjection.base().translate(LegacyScopeProjection.EYE_X+1-lensCenter.x,
                    LegacyScopeProjection.EYE_Y-lensCenter.y,-lensCenter.z);
            var reference=ScopeMaskRenderer.frame(rig,optic,toEye,0,70);
            for(float unit:new float[]{.6F,2F}) {
                var same=ScopeMaskRenderer.frame(rig,optic,new Matrix4f().scaling(unit).mul(toEye),0,70);
                near(same.scale(),reference.scale(),optic.shortName+" native mask size is invariant under eye-space scaling");
                // Single-precision inversion after changing all camera units accumulates
                // several ULPs, especially for the NF7x's short pupil distance.
                check(Math.abs(same.fov()-reference.fov())<=Math.max(1E-5,64*Math.ulp((float)reference.fov())),
                        optic.shortName+" native mask projection is invariant under eye-space scaling: "+same.fov()+" vs "+reference.fov());
            }
            nativeMasks++;
        }
        check(nativeMasks>=5,"authored mask scale exercised across scope families");
        System.out.println("VerifyScopeEyeRelief: "+checks+" checks passed; real PSO-1, moving eye, lens clipping, both hands");
    }
    private static boolean hasScope(AttachmentType at){return at.scopeGlassModel!=null&&!at.scopeGlassModel.isBlank()&&!at.scopeGlassModel.equalsIgnoreCase("none")&&!at.isFoldedVariant();}
}
