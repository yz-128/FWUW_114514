package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.AttachmentType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** FMUM's authored mask mesh/atlas projected in its square scope framebuffer, then on the ocular. */
public final class ScopeMaskRenderer {
    private ScopeMaskRenderer() { }
    public record Frame(float scale,float depth,float y,float z,float roll,double fov) { }
    /** Compatibility entry point for tools that start with the undivided hand FOV. */
    public static Frame frame(BakedRig rig,AttachmentType type,Matrix4f toEye,int step,double baseFov) {
        if(rig==null || rig.animation==null || toEye==null || !toEye.isFinite())return null;
        var eye=new Matrix4f(toEye).invert().transformPosition(new Vector3f());
        float distance=FmumSightPicture.pupilDistance(rig.animation,type.modelScale,eye);
        double imageFov=ScopeMagnification.nativeFov(baseFov,type,step,distance);
        var up=LegacyScopeProjection.physicalToLegacy(toEye).transformDirection(new Vector3f(0,1,0));
        return frameInImage(rig,type,toEye,step,imageFov,(float)Math.atan2(up.z,up.y));
    }
    /** The world camera has already applied rawZoom * pupilDistance to imageFov. */
    public static Frame frameInImage(BakedRig rig,AttachmentType type,Matrix4f toEye,int step,
                                     double imageFov,float dotRoll) {
        if(rig==null || rig.animation==null || toEye==null || !toEye.isFinite())return null;
        var model=rig.animation;var eye=new Matrix4f(toEye).invert().transformPosition(new Vector3f());
        float distance=FmumSightPicture.pupilDistance(model,type.modelScale,eye);
        if(!eye.isFinite() || !Double.isFinite(imageFov) || imageFov<=0 || imageFov>=180
                || !Float.isFinite(distance) || !Float.isFinite(dotRoll))return null;
        int index=Math.clamp(step,0,Math.max(0,type.fovZoomLevels.length-1));
        float raw=type.fovZoomLevels.length==0?type.FOVZoomLevel:type.fovZoomLevels[index];
        float first=type.fovZoomLevels.length==0?raw:type.fovZoomLevels[0];
        float factor=(float)Math.min(1,FmumActionPose.scalar(model,"minPupilDistance",.3F)
                /Math.pow(distance,FmumActionPose.scalar(model,"pupilDistancePower",3)));
        float scale=FmumActionPose.scalar(model,"scopeMaskScale",.02F)*first/Math.max(.001F,raw)/distance*factor;
        var offset=ScopeEyeRelief.maskOffset(model,toEye);
        if(offset==null || !Float.isFinite(scale) || scale<=0)return null;
        float depth=FmumActionPose.scalar(model,"scopeMaskXTrans",-.409375F)-FmumFirstPersonPose.DEFAULT_EYE.x;
        return depth>0?new Frame(scale,depth,offset.y(),offset.z(),dotRoll,imageFov):null;
    }
    private static final java.util.Map<String,String> MASK_STATE=new java.util.concurrent.ConcurrentHashMap<>();
    /** Draws the authored black mask into the square scope framebuffer, as FMUM's scope layer does. */
    public static void bake(MultiBufferSource buffers,BakedRig rig,AttachmentType type,Matrix4f toEye,
                            int frame,int step,double baseFov,float alpha) {
        bake(buffers,rig,type,frame(rig,type,toEye,step,baseFov),frame,alpha);
    }
    /** Scope-framebuffer branch: mask is drawn last, at the native fixed Y=.4 plane. */
    public static void bake(MultiBufferSource buffers,BakedRig rig,GunSightState.Sight scope,
                            Matrix4f toEye,double imageFov,float ads) {
        var type=scope.type();
        float roll=(float)Math.toRadians((scope.roll()-type.aimCenterX)*(1-Math.clamp(ads,0,1)));
        var params=frameInImage(rig,type,toEye,scope.node().zoomStep,imageFov,roll);
        // RenderGun restores white/alpha=1 before this pass. The off-axis picture alpha
        // belongs to the physical glass, rather than a second multiplication in IMAGE.
        bake(buffers,rig,type,params,scope.node().dotFrame,1);
    }
    private static void bake(MultiBufferSource buffers,BakedRig rig,AttachmentType type,Frame params,
                             int frame,float alpha) {
        if(params==null)return;
        var skin=Textures.forReticle(type,frame);if(skin==null)return;
        var mask=ModelCache.rigForReference(type.blackMaskModel,1F,skin);if(mask==null)return;
        var buffer=buffers.getBuffer(ScopeFramebufferOverlays.type(skin));
        for(var quad:mask.quads) {
            var polygon=cameraMaskPolygon(quad,params);
            if(polygon.size()<3)continue;
            for(int triangle=1;triangle+1<polygon.size();triangle++) {
                var a=polygon.getFirst();var b=polygon.get(triangle);var c=polygon.get(triangle+1);
                ScopeFramebufferOverlays.cameraTriangle(buffer,a,b,c,1F,alpha);
            }
        }
    }

    /** Actual bake path retains depth for perspective-correct atlas interpolation on the GPU. */
    public static List<LegacyScopeProjection.Vertex> cameraMaskPolygon(BakedRig.Quad quad,Frame params) {
        // RenderGun:1215-1219 runs after the virtual gun matrix was popped:
        // Mbase * T(maskX, .4+offsetY, offsetZ) * Rx(dotRotate) * S(1,scale,scale).
        // In particular .4 is a Y coordinate, never a perspective depth divisor.
        var pose=LegacyScopeProjection.base()
                .translate(params.depth+FmumFirstPersonPose.DEFAULT_EYE.x,.4F+params.y,params.z)
                .rotateX(params.roll).scale(1,params.scale,params.scale);
        var camera=new ArrayList<LegacyScopeProjection.Vertex>();
        for(int i=0;i<quad.positions().length;i++) {
            var p=pose.transformPosition(new Vector3f(quad.positions()[i]));
            var uv=quad.uvs()[i];
            camera.add(new LegacyScopeProjection.Vertex(p.x,p.y,p.z,uv[0],uv[1]));
        }
        // The native mask sits ~.06 blocks from the GL camera, outside the .05 hand near plane.
        // Clip in camera space before dividing; discarding a single crossing vertex made a
        // whole face disappear, whereas emitting it unchecked could cover the entire image.
        return LegacyScopeProjection.clipCameraPolygon(camera,params.fov,.05F);
    }

    /** Historical lens diagnostics need image coordinates; actual baking uses cameraMaskPolygon. */
    public static List<ScopeReticleRenderer.Projected> projectMask(BakedRig.Quad quad,Frame params) {
        var clipped=cameraMaskPolygon(quad,params);
        var result=new ArrayList<ScopeReticleRenderer.Projected>();
        for(var p:clipped) {
            var uv=LegacyScopeProjection.project(new Vector3f(p.x(),p.y(),p.z()),params.fov);
            if(uv==null)return List.of();
            result.add(new ScopeReticleRenderer.Projected(uv[0],uv[1],p.textureU(),p.textureV()));
        }
        return result;
    }

    public static void render(PoseStack lensPose,MultiBufferSource buffers,BakedRig rig,AttachmentType type,
                              BakedRig glass,Matrix4f toEye,int frame,int step,double baseFov,int overlay,boolean mirrored) {
        var params=frame(rig,type,toEye,step,baseFov);if(params==null || glass==null)return;
        var skin=Textures.forReticle(type,frame);if(skin==null)return;
        var mask=ModelCache.rigForReference(type.blackMaskModel,1F,skin);if(mask==null)return;
        if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled()) {
            String state=String.format(java.util.Locale.ROOT,"scale=%.5f depth=%.3f y=%.3f z=%.3f roll=%.3f fov=%.2f quads=%d",
                    params.scale,params.depth,params.y,params.z,params.roll,params.fov,mask.quads.size());
            if(!state.equals(MASK_STATE.put(type.shortName,state)))
                com.flansmod.common.FlansMod.LOGGER.debug("[ScopeMask] "+type.shortName+" "+state);
        }
        var buffer=buffers.getBuffer(ScopeRenderLayers.overlay(skin));
        for(var quad:mask.quads) {
            var polygon=projectMask(quad,params);
            if(polygon.size()<3)continue;
            for(var face:glass.quads) {
                if(face.nx()>=-.9F)continue;
                float[][] aperture=new float[face.uvs().length][];
                for(int i=0;i<aperture.length;i++)aperture[i]=ScopeReticleRenderer.imageUv(glass,face.uvs()[i][0],face.uvs()[i][1]);
                var clipped=ScopeReticleRenderer.clip(polygon,aperture);
                for(int triangle=1;triangle+1<clipped.size();triangle++) {
                    var vertices=new ScopeReticleRenderer.Projected[]{clipped.getFirst(),clipped.get(triangle),clipped.get(triangle+1)};
                    for(int index:mirrored?new int[]{2,1,0,0}:new int[]{0,1,2,2}) {
                        var vertex=vertices[index];var uv=ScopeReticleRenderer.authoredUv(glass,vertex.u(),vertex.v());
                        var position=ScopeReticleRenderer.glassPosition(face,uv[0],uv[1]);if(position==null)continue;
                        buffer.addVertex(lensPose.last().pose(),position.x-.002F,position.y,position.z)
                                .setUv(vertex.textureU(),vertex.textureV()).setColor(1F,1F,1F,1F)
                                .setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT)
                                .setNormal(lensPose.last(),face.nx(),face.ny(),face.nz());
                    }
                }
            }
        }
    }

}
