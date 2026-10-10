import com.flansmod.client.model.FmumShotEffects;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import org.joml.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Real muzzle mounts, native vertex consumer emission and render state, without any GL calls. */
public final class VerifyMuzzleGlow {
 static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
 static void near(Vector3f a,Vector3f b,String detail){check(a.distance(b)<.0002F,detail+": "+a+" vs "+b);}
 record Vertex(Vector3f pos,int red,int green,int blue,int alpha){}
 static final class Recorder implements VertexConsumer {
  final List<Vertex> vertices=new ArrayList<>();Vector3f point;
  public VertexConsumer addVertex(float x,float y,float z){point=new Vector3f(x,y,z);return this;}
  public VertexConsumer setColor(int r,int g,int b,int a){vertices.add(new Vertex(point,r,g,b,a));return this;}
  public VertexConsumer setUv(float u,float v){throw new AssertionError("untextured halo should not emit UV");}
  public VertexConsumer setUv1(int u,int v){throw new AssertionError("halo does not depend on overlay");}
  public VertexConsumer setUv2(int u,int v){throw new AssertionError("halo is emissive without lightmap");}
  public VertexConsumer setNormal(float x,float y,float z){throw new AssertionError("unlit shader needs no normal");}
 }
 static Object field(Object owner,String name)throws Exception {
  var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
 }
 static Object shard(String name)throws Exception {var f=RenderStateShard.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 static FmumMuzzlePose.Flash config(String model,float scale,float fade){return new FmumMuzzlePose.Flash(model,"flash",scale,fade,1,new Matrix4f(),true,"fixture");}
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();int guns=0,active=0,samples=0;
  for(var type:pack.types)if(type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG && !gun.magazineNames.isEmpty()) {
   guns++;var model=ModelCache.rigFor(gun,gun.modelScale).animation;var flash=FmumMuzzlePose.flash(gun,model);
   check(flash!=null,"real muzzle configuration: "+gun.shortName);
   var state=new FmumShotEffects();check(FmumMuzzleGlow.frame(new Matrix4f(),new Matrix4f(),flash,state.alpha(1))==null,"idle gun has no halo");
   state.shoot(gun,flash,0,73,true);
   float previous=1;
   for(int tick=0;tick<12;tick++) {
    float alpha=state.alpha(1);var frame=FmumMuzzleGlow.frame(new Matrix4f().scale(.4F),new Matrix4f(),flash,alpha);
    check(alpha<=previous,"halo follows original flash decay");previous=alpha;
    if(flash.model()==null)check(frame==null,"pack suppresses flash model: "+gun.shortName);
    else if(alpha>=1F/255F)check(frame!=null && frame.intensity()==alpha,"shot fade reused without another clock: "+gun.shortName);
    else check(frame==null,"expired flash does not leave glow behind");
    state.tick();
   }
   if(flash.model()==null)continue;
   active++;
   for(float yaw:new float[]{-179,-90,0,67,179})for(float pitch:new float[]{-70,0,55})for(float scale:new float[]{.2F,.4F,.625F}) {
    var gunPose=new Matrix4f().translate(2.1F,-.7F,-5F).rotateY(.73F).rotateX(-.42F).rotateZ(.2F).scale(scale);
    var view=new Matrix4f().rotateX((float)java.lang.Math.toRadians(pitch)).rotateY((float)java.lang.Math.toRadians(yaw));
    for(var shader:List.of(new Matrix4f(view),new Matrix4f(),new Matrix4f().rotateZ(.2F).mul(view))) {
     var frame=FmumMuzzleGlow.frame(gunPose,shader,flash,.8F);check(frame!=null && frame.pose().isFinite() && frame.radius()>0,"finite sized halo");
     var combined=new Matrix4f(shader).mul(frame.pose());
     var expected=new Matrix4f(shader).mul(gunPose).mul(flash.mount()).getTranslation(new Vector3f());expected.z+=.002F;
     near(combined.getTranslation(new Vector3f()),expected,"halo follows attachment-resolved muzzle: "+gun.shortName);
     near(combined.transformDirection(new Vector3f(1,0,0)),new Vector3f(1,0,0),"billboard horizontal axis in view space");
     near(combined.transformDirection(new Vector3f(0,1,0)),new Vector3f(0,1,0),"billboard vertical axis in view space");
     check(java.lang.Math.abs(frame.radius()-.24F*flash.scale()*scale)<.00001F,"hand/model frame scaling counted once");
     var recorder=new Recorder();FmumMuzzleGlow.emit(recorder,frame);
     check(recorder.vertices.size()==288,"72 quads in three rings");
     int clear=0,lit=0;
     for(var vertex:recorder.vertices) {
      check(vertex.pos().isFinite(),"finite emitted vertices");
      var p=shader.transformPosition(new Vector3f(vertex.pos()));
      check(java.lang.Math.abs(p.z-expected.z)<.0002F,"all emitted vertices on billboard plane");
      check(p.distance(expected)<=frame.radius()+.0002F,"halo bounds follow flash size");
      check(vertex.red()>=vertex.green() && vertex.green()>=vertex.blue(),"warm halo colour");
      check(vertex.alpha()>=0 && vertex.alpha()<=143,"alpha follows requested intensity");
      if(vertex.alpha()==0)clear++;else lit++;
     }
     check(clear==48 && lit==240,"transparent outer edge and visible inner rings");samples++;
    }
   }
  }
  check(guns==31,"31 EFT real muzzle/attachment chains");
  for(var flash:Arrays.asList(null,config(null,.5F,.5F),config("m",0,.5F),config("m",.5F,0)))
   check(FmumMuzzleGlow.frame(new Matrix4f(),new Matrix4f(),flash,1)==null,"no halo for absent/disabled/suppressed flash");
  var fixture=config("m",.5F,.5F);
  var bright=FmumMuzzleGlow.frame(new Matrix4f(),new Matrix4f(),fixture,1);var dim=FmumMuzzleGlow.frame(new Matrix4f(),new Matrix4f(),fixture,.25F);
  var a=new Recorder();var b=new Recorder();FmumMuzzleGlow.emit(a,bright);FmumMuzzleGlow.emit(b,dim);
  check(b.vertices.getFirst().alpha()<a.vertices.getFirst().alpha()/3,"alpha meaningfully dims actual geometry");
  var type=MuzzleGlowRenderer.TYPE;var state=field(type,"state");
  check(type.format()==DefaultVertexFormat.POSITION_COLOR && type.mode()==VertexFormat.Mode.QUADS,"native colour-only lightning shader format");
  check(field(state,"shaderState")==shard("RENDERTYPE_LIGHTNING_SHADER"),"unlit shader");
  check(field(state,"transparencyState")==shard("LIGHTNING_TRANSPARENCY"),"native additive blending");
  check(field(state,"depthTestState")==shard("LEQUAL_DEPTH_TEST"),"normal wall/gun occlusion");
  check(field(state,"writeMaskState")==shard("COLOR_WRITE"),"transparent halo never writes depth");
  check(field(state,"cullState")==shard("NO_CULL"),"visible from front and back cameras");
  var node=new ClassNode();try(var in=Files.newInputStream(Path.of("build/classes/java/main/com/flansmod/client/render/MuzzleEffectsRenderer.class"))){new ClassReader(in).accept(node,0);}
  int frames=0,emits=0;for(var method:node.methods)for(var ins:method.instructions)if(ins instanceof MethodInsnNode call && call.owner.endsWith("FmumMuzzleGlow")){
   if(call.name.equals("frame"))frames++;if(call.name.equals("emit"))emits++;
  }
  check(frames==1&&emits==1,"production held-gun effect path draws tested halo once");
  System.out.printf("PASS: 31 real muzzle chains (%d flash-enabled), %d camera/scale/shader samples, %d emitted vertices, transparent edge/warm colour/shot fade, suppressed/idle exclusions, native additive/depth-tested/non-depth-writing render state, production renderer wiring.%n",active,samples,samples*288);
 }
}
