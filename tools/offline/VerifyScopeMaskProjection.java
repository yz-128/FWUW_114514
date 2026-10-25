import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.List;

/** Native mask equations and production projectMask path; no window or Minecraft launch. */
public final class VerifyScopeMaskProjection {
    private static int checks, scopes, projected;
    private static void check(boolean good,String message) {
        checks++;
        if(!good)throw new AssertionError(message);
    }
    private static void near(double actual,double expected,double error,String label) {
        check(Double.isFinite(actual)&&Math.abs(actual-expected)<=error,
                label+": "+actual+" expected "+expected);
    }
    private static boolean present(String value) {
        return value!=null&&!value.isBlank()&&!value.equalsIgnoreCase("none");
    }
    // Independently assembled from patched ItemRenderer:434, ForgeHooksClient:225,
    // RenderGun:1007. Keep this separate from LegacyScopeProjection.base().
    private static Matrix4f nativeParent() {
        float d=(float)(Math.PI/180);
        return new Matrix4f().translation(.56F,-.52F,-.72F)
                .rotateY(45*d).scale(.4F).translate(0,-.3F,0).scale(1.5F)
                .rotateY(50*d).rotateZ(335*d).translate(-.9375F,-.0625F,0)
                .rotateZ(20*d).rotateY(-5*d).translate(.15F,.375F,-1.005F)
                .rotateX(.5F*d).rotateZ(5*d).translate(0,.7F/160-.03F,-.35F/160);
    }
    public static void main(String[]args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for(String arg:args)new OfflinePack(Path.of(arg)).readTypes();
        var parent=nativeParent();
        var maskQuad=new BakedRig.Quad(new float[][]{{0,-.001F,-.001F},{0,-.001F,.001F},
                {0,.001F,.001F},{0,.001F,-.001F}},new float[][]{{0,0},{1,0},{1,1},{0,1}},-1,0,0);
        // This numeric camera point is an original GL-parent landmark, not an invented
        // .4 perspective depth. Its small residual records the old hardcoded eye approximation.
        var nativeEye=parent.transformPosition(new Vector3f(-81.5F/160,.4F,0));
        near(nativeEye.z,.00034902,2E-6,"original GL eye residual");
        for(var type:AttachmentType.attachments) {
            if(type.isFoldedVariant()||!present(type.scopeGlassModel)||!present(type.blackMaskModel))continue;
            var rig=ModelCache.rigFor(type,type.modelScale);
            if(rig==null||rig.animation==null)continue;
            var model=rig.animation;
            var objective=FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0);
            var mask=ModelCache.rigForReference(type.blackMaskModel,1,Textures.forReticle(type,0));
            check(mask!=null&&!mask.quads.isEmpty(),type.shortName+" authored mask mesh loads");
            for(float roll:new float[]{0,37,-90}) {
                var rotation=new Matrix4f().rotationX((float)Math.toRadians(roll));
                var rotatedObjective=rotation.transformPosition(new Vector3f(objective));
                // Put the true objective .5 blocks ahead, with a known signed offset.
                for(float vertical:new float[]{0,.01F,-.01F}) {
                    float lateral=vertical*.5F;
                    var tracked=new Matrix4f().translation(-81.5F/160+.5F-rotatedObjective.x,
                            .4F+vertical-rotatedObjective.y,lateral-rotatedObjective.z).mul(rotation);
                    var toEye=new Matrix4f(parent).mul(tracked);
                    var offset=ScopeEyeRelief.maskOffset(model,toEye);
                    check(offset!=null,type.shortName+" native signed objective offset");
                    double expectedY=-vertical/Math.hypot(.5,lateral)
                            *FmumActionPose.scalar(model,"scopeMaskMultTransY",.4F);
                    double expectedZ=-lateral/.5*FmumActionPose.scalar(model,"scopeMaskMultTransZ",.4F);
                    near(offset.y(),expectedY,2E-6,type.shortName+" mask Y matches RenderGun angle");
                    near(offset.z(),expectedZ,2E-6,type.shortName+" mask Z matches RenderGun angle");
                    for(int step=0;step<Math.max(1,type.fovZoomLevels.length);step++) {
                        var frame=ScopeMaskRenderer.frameInImage(rig,type,toEye,step,12.5,0);
                        check(frame!=null,type.shortName+" mask parameters valid");
                        near(frame.fov(),12.5,0,"image FOV is not divided by optical power twice");
                        var eye=new Matrix4f(toEye).invert().transformPosition(new Vector3f());
                        var pupil=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(type.modelScale);
                        double distance=Math.max(.001F,pupil.distance(eye));
                        float first=type.fovZoomLevels.length==0?type.FOVZoomLevel:type.fovZoomLevels[0];
                        float current=type.fovZoomLevels.length==0?first:type.fovZoomLevels[step];
                        double scale=FmumActionPose.scalar(model,"scopeMaskScale",.02F)*first/Math.max(.001F,current)/distance
                                *Math.min(1,FmumActionPose.scalar(model,"minPupilDistance",.3F)
                                /Math.pow(distance,FmumActionPose.scalar(model,"pupilDistancePower",3)));
                        near(frame.scale(),scale,2E-6,type.shortName+" original getScopeMaskScale * min(1,pupilFactor)");
                        var cameraPose=new Matrix4f(parent).translate(
                                FmumActionPose.scalar(model,"scopeMaskXTrans",-.409375F),
                                .4F+frame.y(),frame.z()).rotateX(frame.roll()).scale(1,frame.scale(),frame.scale());
                        var small=ScopeMaskRenderer.projectMask(maskQuad,frame);
                        var cameraPolygon=ScopeMaskRenderer.cameraMaskPolygon(maskQuad,frame);
                        if(vertical==0) {
                            check(small.size()==4,type.shortName+" native fixed-Y plane stays in front of near clip");
                            check(cameraPolygon.size()==4,type.shortName+" bake retains complete camera-space mask face");
                            for(int i=0;i<4;i++) {
                                var camera=cameraPose.transformPosition(new Vector3f(maskQuad.positions()[i]));
                                var submitted=cameraPolygon.get(i);
                                near(submitted.x(),camera.x,2E-6,type.shortName+" bake retains original camera X");
                                near(submitted.y(),camera.y,2E-6,type.shortName+" bake retains original camera Y");
                                near(submitted.z(),camera.z,2E-6,type.shortName+" bake retains original camera depth for perspective UVs");
                                double focal=.5/Math.tan(Math.toRadians(12.5)/2);
                                near(small.get(i).u(),.5+camera.x/-camera.z*focal,2E-6,type.shortName+" mask U uses native camera depth");
                                near(small.get(i).v(),.5-camera.y/-camera.z*focal,2E-6,type.shortName+" mask V uses native camera depth");
                            }
                        }
                        for(var quad:mask.quads)for(var p:ScopeMaskRenderer.projectMask(quad,frame)) {
                            check(Float.isFinite(p.u()+p.v()+p.textureU()+p.textureV()),"finite clipped authored mask");
                            check(p.u()>=-1E-4&&p.u()<=1.0001&&p.v()>=-1E-4&&p.v()<=1.0001,
                                    "mask triangles are clipped to square image");
                            projected++;
                        }
                    }
                }
            }
            var behind=new Matrix4f(parent).translate(-2,0,0);
            check(ScopeEyeRelief.maskOffset(model,behind)==null,"behind-eye objective is rejected");
            scopes++;
        }
        check(scopes>=5,"real mask families exercised");
        check(projected>0,"actual authored mask vertices projected");
        System.out.println("VerifyScopeMaskProjection: "+checks+" checks passed; "+scopes+" optics; "+projected+" clipped mask vertices");
    }
}
