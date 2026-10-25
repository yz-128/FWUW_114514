import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Actual user's G33/EOTech assembly at both saved rail positions; native square-image pixels. */
public final class VerifyG33Picture {
 static int checks;static final int W=1280,H=720;
 static void check(boolean good,String message){checks++;if(!good)throw new AssertionError(message);}
 static void sample(float rail,Path out,int scene)throws Exception {
  var view=VerifyMountedScopes.mounted("G33Enabled",rail);
  var side=view.defaultAttachments.get(6).getFirst();var holo=new ModifiableType.DefaultAttachment();holo.name="EOTech552";holo.slot=0;holo.pos=rail;side.children.add(holo);
  var scope=GunSightState.scopeSight(view);check(scope!=null,"real G33 line is active");
  var type=scope.type();var rig=ModelCache.rigFor(type,type.modelScale);var glass=ModelCache.rigForReference(type.scopeGlassModel,1);
  var weapon=VerifyMountedScopes.gunPose(view);var toEye=new Matrix4f(weapon).translate(ScopeReticleRenderer.mount(scope));
  var pupil=FmumSightPicture.vector(rig.animation,"pupilRefCenter",0,0,0).mul(type.modelScale);
  var size=FmumSightPicture.vector(rig.animation,"scopeGlassModelScale",1,1,1).mul(type.modelScale);
  var lens=new PoseStack();lens.mulPose(new Matrix4f(toEye).translate(pupil).scale(size));
  var projection=new Matrix4f().perspective((float)java.lang.Math.toRadians(70),W/(float)H,.005F,100);
  RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
  float distance=FmumSightPicture.pupilDistance(rig.animation,type.modelScale,new Matrix4f(toEye).invert().transformPosition(new Vector3f()));
  double nativeFov=70/(39*distance);
  check(java.lang.Math.abs(nativeFov-ScopeMagnification.nativeFov(70,type,0,distance))<1E-5,"independent native raw zoom / measured eye-distance formula");
  RenderSystem.clearColor(.38F,.5F,.63F,1);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);GL11.glClearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
  VerifyMountedScopes.body(view,weapon,projection);VerifyMountedScopes.children(view,view.defaultAttachments.stream().flatMap(List::stream).toList(),weapon,projection);
  var world=new VerifyScopeComposition.Capture();FmumSightRenderer.renderLensSurface(lens,world,glass,new Matrix4f(projection).mul(lens.last().pose()),1,false);
  ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(world,scene,projection));
  var reticle=new VerifyScopeComposition.Capture();ScopeReticleRenderer.render(lens,reticle,view,scope,rig,glass,toEye,projection,nativeFov,0,false);
  int dot=VerifyMountedScopes.reticleTexture(AttachmentType.getAttachment("EOTech552"),0);
  ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(reticle,dot,projection));GL11.glDeleteTextures(dot);
  var shadow=new VerifyScopeComposition.Capture();ScopeMaskRenderer.render(lens,shadow,rig,type,glass,toEye,0,0,70,0,false);
  int mask=VerifyMountedScopes.reticleTexture(type,0);ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(shadow,mask,projection));GL11.glDeleteTextures(mask);
  var picture=VerifyScopeComposition.pixels();String label=String.format(Locale.ROOT,"G33-EOTech-rail%.4f",rail);ImageIO.write(picture,"png",out.resolve(label+".png").toFile());
  int centre=0,outer=0;int[] quadrants=new int[4];float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
  for(var q:glass.quads)for(var p:q.positions()){var v=new Matrix4f(projection).mul(lens.last().pose()).transform(new Vector4f(p[0],p[1],p[2],1));min=java.lang.Math.min(min,v.x/v.w);max=java.lang.Math.max(max,v.x/v.w);}
  double radius=(max-min)*W*.25;
  for(int y=0;y<H;y++)for(int x=0;x<W;x++){int c=picture.getRGB(x,y);if(((c>>16)&255)>200 && ((c>>8)&255)<80 && (c&255)<80){double dx=x-W*.5,dy=y-H*.5,d=java.lang.Math.hypot(dx,dy)/radius;if(d<.2)centre++;if(d>.45 && d<1.1){outer++;quadrants[(dx<0?0:1)+(dy<0?0:2)]++;}}}
  System.out.printf(Locale.ROOT,"%s pupilDistance=%.6f FOV=%.5f lensDiameter=%.1f px centre=%d ring=%d quadrants=%s%n",label,distance,nativeFov,radius*2,centre,outer,Arrays.toString(quadrants));
  check(centre>0,"real centre dot survives composition "+label);
  if(rail<.12F){check(outer>centre,"native reference position retains the ring, not just its dot");for(int q:quadrants)check(q>0,"reference ring visible in every quadrant");}
  check(GL11.glGetError()==GL11.GL_NO_ERROR,"G33 pixel composition GPU state");
 }
 public static void main(String[] args)throws Exception {
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();VerifyMountedScopes.packs.add(pack);Path out=Path.of(args[1]);Files.createDirectories(out);
  RenderSystem.initRenderThread();check(GLFW.glfwInit(),"GLFW");GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
  long window=GLFW.glfwCreateWindow(W,H,"native G33 picture",0,0);check(window!=0,"invisible GPU context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();VerifyScopeComposition.initShader();var target=new TextureTarget(W,H,true,false);
  var image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<128;y++)for(int x=0;x<128;x++)image.setRGB(x,y,0xFF99BBDD);int scene=VerifyScopeComposition.texture(image);var white=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);white.setRGB(0,0,0xFFFFFFFF);int overlay=VerifyScopeComposition.texture(white);
  try {RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(overlay);RenderSystem.activeTexture(GL13.GL_TEXTURE0);target.bindWrite(true);sample(.1125F,out,scene);sample(.15F,out,scene);}
  finally {target.destroyBuffers();GL11.glDeleteTextures(scene);GL11.glDeleteTextures(overlay);for(int id:VerifyMountedScopes.textures.values())GL11.glDeleteTextures(id);GL15.glDeleteBuffers(VerifyScopeComposition.vbo);GL30.glDeleteVertexArrays(VerifyScopeComposition.vao);GL20.glDeleteProgram(VerifyScopeComposition.program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
  System.out.println("PASS VerifyG33Picture: "+checks+" checks; real EOTech/G33 geometry, original red ring/dot atlas, both saved rail positions and native square composition. Synthetic GPU scene; no game.");
 }
}
