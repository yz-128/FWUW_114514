import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.joml.Vector3f;
import java.nio.file.Path;

/** Independent source constants from FMUM-1710 RenderGun/SightCoor plus real pack bytecode. */
public final class VerifyNativeScopeRules {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static void near(double actual,double expected,String label){check(Double.isFinite(actual)&&Math.abs(actual-expected)<1E-5,label+": "+actual+" != "+expected);}
    public static void main(String[] args)throws Exception {
        for(String arg:args)new OfflinePack(Path.of(arg)).readTypes();
        var gun=VerifyMountedScopes.mounted("SeecterDR1x4x",.15F);var pose=VerifyMountedScopes.gunPose(gun);
        float eyeX=FmumFirstPersonPose.eyeInGunSpace(pose).x;
        // RenderGun records pupil co-ordinates after aimingTranslate, not before it.
        double nativeEye=-81.5/160-FmumActionPose.vector(ModelCache.load(gun),"aimingTranslate")[0];
        check(Math.abs(eyeX-nativeEye)<.001,"live hand preserves native authored axial placement");
        var sight=com.flansmod.common.GunSightState.scopeSight(gun);var type=sight.type();var rig=ModelCache.rigFor(type,type.modelScale);
        var toEye=new Matrix4f(pose).translate(ScopeReticleRenderer.mount(sight));
        var frame=ScopeMaskRenderer.frame(rig,type,toEye,0,70);
        // ModelSeecterDR1x4x bytecode 22438: pupil x=-.921875; scale=.2.
        // Include the translated eye and the actual .15 SVD rail position.
        double distance=Math.abs(-.921875*.2-.05625+.15-eyeX);
        near(frame.fov(),70/(48.75*distance),"RenderGun uses raw zoom * measured pupil distance");
        near(frame.scale(),.01/distance,"SightCoor native mask scale");
        near(frame.depth(),.1,"native mask plane is .1 blocks in front of the eye");
        var low=ScopeMaskRenderer.frame(rig,type,toEye,1,70);
        near(low.scale(),.01*(48.75/15.5)/distance,"native step-dependent mask scale");
        near(low.fov(),70/(15.5*distance),"native low-power scope hand projection");
        var elcan=ModelCache.load(AttachmentType.getAttachment("ELCANSpecterDR1x4x"));
        near(elcan.fmumScalars.get("scopeMaskMultRotY"),.45,"chained Octagon rotation Y retained");
        near(elcan.fmumScalars.get("scopeMaskMultRotZ"),.45,"chained Octagon rotation Z retained");
        near(elcan.fmumScalars.get("scopeMaskMultTransY"),.36,"chained Octagon translation Y retained");
        near(elcan.fmumScalars.get("scopeMaskMultTransZ"),.36,"chained Octagon translation Z retained");
        for(float ads:new float[]{0,.25F,.5F,.75F,1}){
            var base=new Matrix4f().translate(.56F,-.52F,-.72F);FmumFirstPersonPose.itemFrame(base);FmumFirstPersonPose.placement(base,ModelCache.load(gun),ads,0,0);
            var eye=FmumFirstPersonPose.eyeInGunSpace(base);var before=new Matrix4f(base);FmumFirstPersonPose.aim(base,gun,ads,eye);
            near(FmumFirstPersonPose.eyeInGunSpace(base).x,eye.x,"aim pivot preserves authored axial placement at ADS "+ads);
            if(ads==0)check(base.equals(before,1E-6F),"hip pose unchanged");
        }
        var plain=gun.withAttachments(java.util.List.of());var base=new Matrix4f().rotationY((float)Math.PI/2).translate(.35F,-.2F,.1F);
        var eye=FmumFirstPersonPose.eyeInGunSpace(base);FmumFirstPersonPose.aim(base,plain,1,eye);
        near(FmumFirstPersonPose.eyeInGunSpace(base).x,eye.x,"unscoped guns retain their axial pose");
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,.05F,100);
        var close=FmumSightRenderer.nearProjection(projection,.005F);
        near(close.m00(),projection.m00(),"near-plane does not change horizontal FOV");
        near(close.m11(),projection.m11(),"near-plane does not change vertical FOV");
        near(close.m32()/(close.m22()-1),.005,"close rear glass has a suitable hand near-plane");
        near(ScopeMagnification.squareRenderFov(70,1280,720),70,"landscape native square FOV");
        double portrait=ScopeMagnification.squareRenderFov(70,720,1280);
        near(Math.tan(Math.toRadians(portrait)/2)*720/1280,Math.tan(Math.toRadians(70)/2),"portrait native square angular span");
        // The nested camera must not inherit the outer hand pass's queued gun geometry: the shared
        // BufferSource is flushed by the nested LevelRenderer and would otherwise draw the gun into
        // the ocular. Verify the flush call exists and comes before renderLevel in prepare().
        var scopeNode=new ClassNode();
        try(var in=VerifyNativeScopeRules.class.getResourceAsStream("/com/flansmod/client/render/ScopeWorldTexture.class")){
            new ClassReader(in.readAllBytes()).accept(scopeNode,0);
        }
        boolean hasFlush=false,flushBeforeRender=false;
        for(var m:scopeNode.methods)if(m.name.equals("flushPendingHandGeometry"))hasFlush=true;
        for(var m:scopeNode.methods)if(m.name.equals("prepare")){
            boolean flushed=false;
            for(var ins:m.instructions){
                if(ins instanceof MethodInsnNode call){
                    if(call.owner.equals("com/flansmod/client/render/ScopeWorldTexture")
                            &&call.name.equals("flushPendingHandGeometry"))flushed=true;
                    if(call.owner.equals("net/minecraft/client/Minecraft")&&call.name.equals("renderBuffers"))flushed=true;
                    if(call.owner.equals("com/mojang/blaze3d/vertex/MultiBufferSource$BufferSource")
                            &&(call.name.equals("endBatch")||call.name.equals("endLastBatch")))flushed=true;
                    if(call.owner.equals("net/minecraft/client/renderer/GameRenderer")&&call.name.equals("renderLevel")
                            &&flushed)flushBeforeRender=true;
                }
            }
        }
        check(hasFlush,"scope camera clears pending hand geometry");
        check(flushBeforeRender,"hand geometry flush happens before the nested world render");
        System.out.println("PASS: "+checks+" native FMUM source-rule checks; axial eye, mask FOV/scale/steps, chained .class fields and ADS/hip isolation.");
    }
}
