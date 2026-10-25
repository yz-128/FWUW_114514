import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.joml.*;
import org.lwjgl.opengl.*;

/** Independent 3-D raster reference for FMUM RenderGun's scope reticle/mask branches.
 * Unlike production's polygon projection, these original meshes go through the GPU camera
 * into an actual square framebuffer. Gun/effect geometry is outside this reference's scope. */
final class NativeScopeReference {
 static TextureTarget render(GunType view,GunSightState.Sight scope,BakedRig rig,Matrix4f toEye,int scene,int resolution)throws Exception {
  var type=scope.type();var frame=ScopeMaskRenderer.frame(rig,type,toEye,scope.node().zoomStep,70);
  var target=new TextureTarget(resolution,resolution,false,false);target.bindWrite(true);
  RenderSystem.clearColor(0,0,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
  var fill=new VerifyScopeComposition.Capture();float[][] p={{-1,-1},{1,-1},{1,1},{-1,1}};
  for(float[] v:p)fill.addVertex(v[0],v[1],0).setUv((v[0]+1)/2,(v[1]+1)/2).setColor(1F,1F,1F,1F);
  ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(fill,scene,new Matrix4f()));
  if(frame==null || frame.fov()<=0 || frame.fov()>=180)return target;
  var projection=new Matrix4f().perspective((float)java.lang.Math.toRadians(frame.fov()),1,.0001F,100);
  var eye=ScopeReticleRenderer.scopeEye(rig.animation);
  var optical=new Matrix4f(toEye).setTranslation(0,0,0).translate(new Vector3f(eye).negate());
  for(var sight:GunSightState.selectedGroup(view).sights()) {
   var at=sight.type();if(at.isFoldedVariant() || at.lightStuffModel==null || at.lightStuffModel.equalsIgnoreCase("none"))continue;
   var model=ModelCache.load(at);var border=model.fmumVectorArrays.get("reticleBorder");if(border==null || border.length<3)continue;
   var transform=ScopeReticleRenderer.intoScope(sight,scope);float[][] transformed=new float[border.length][3];
   for(int i=0;i<border.length;i++){var v=transform.transformPosition(new Vector3f(border[i]));transformed[i]=new float[]{v.x,v.y,v.z};}
   var normal=transform.transformDirection(FmumSightPicture.vector(model,"normalVecOfDotBorder",1,0,0));
   var targets=model.fmumVectorArrays.getOrDefault("reticleAt",new float[][]{{0,0,0}});
   var dot=ModelCache.rigForReference(at.lightStuffModel,1,Textures.forReticle(at,sight.node().dotFrame));
   float scale=FmumActionPose.scalar(model,"reticleScale",1);
   if(FmumActionPose.scalar(model,"relativeReticleSize",0)!=0 && at.fovZoomLevels.length>0)scale*=at.fovZoomLevels[0]/at.fovZoomLevels[java.lang.Math.clamp(sight.node().zoomStep,0,at.fovZoomLevels.length-1)];
   int texture=VerifyMountedScopes.reticleTexture(at,sight.node().dotFrame);
   try {for(int i=0;i<targets.length;i++) {
    var far=transform.transformPosition(new Vector3f(targets[i]));var hit=FmumSightPicture.intersection(eye,far,transformed,normal);if(hit==null)continue;
    var quads=targets.length==1?dot.quads:dot.quadsByRenderer.get("defaultModel#"+i);if(quads==null)continue;
    var pose=new PoseStack();pose.mulPose(new Matrix4f(optical).translate(hit).mul(new Matrix4f(transform).setTranslation(0,0,0)).scale(1,scale,scale));
    var capture=new VerifyScopeComposition.Capture();RigRenderer.renderPart(pose,capture,quads.stream().filter(q->q.nx()<-.001F).toList(),15728880,0,1,1,1,1,false);
    ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(capture,texture,projection));
   }}finally{com.mojang.blaze3d.platform.GlStateManager._deleteTexture(texture);}
  }
  var mask=ModelCache.rigForReference(type.blackMaskModel,1,Textures.forReticle(type,scope.node().dotFrame));
  if(mask!=null) {
   var pose=new PoseStack();pose.mulPose(new Matrix4f().rotationY((float)(java.lang.Math.PI/2)).translate(frame.depth(),frame.y(),frame.z()).rotateX(frame.roll()).scale(1,frame.scale(),frame.scale()));
   var capture=new VerifyScopeComposition.Capture();RigRenderer.renderPart(pose,capture,mask.quads.stream().filter(q->q.nx()<-.9F).toList(),15728880,0,1,1,1,1,false);
   int texture=VerifyMountedScopes.reticleTexture(type,scope.node().dotFrame);try{ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(capture,texture,projection));}finally{com.mojang.blaze3d.platform.GlStateManager._deleteTexture(texture);}
  }
  return target;
 }
}
