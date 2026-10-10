import com.flansmod.client.model.FmumShotEffects;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.joml.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Real shader API dispatch, real pack meshes, native projection/UV/alpha and per-frame lifecycle; no GL. */
public final class VerifyShaderMuzzleLighting {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 record Vertex(Vector3f point,float u,float v,int alpha){}
 static final class Recorder implements VertexConsumer {
  final List<Vertex> vertices=new ArrayList<>();Vector3f p;float u,v;
  public VertexConsumer addVertex(float x,float y,float z){p=new Vector3f(x,y,z);return this;}
  public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
  public VertexConsumer setColor(int r,int g,int b,int a){check(r==(int)(255*FmumShaderFlash.RED)&&g==(int)(255*FmumShaderFlash.GREEN)&&b==(int)(255*FmumShaderFlash.BLUE),"bounded warm post-composite colour");vertices.add(new Vertex(p,u,v,a));return this;}
  public VertexConsumer setUv1(int u,int v){throw new AssertionError("post-composite unlit shader needs no overlay");}
  public VertexConsumer setUv2(int u,int v){throw new AssertionError("post-composite unlit shader needs no lightmap");}
  public VertexConsumer setNormal(float x,float y,float z){throw new AssertionError("post-composite unlit shader needs no normal");}
 }
 static Object field(Object o,String name)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static Object shard(String name)throws Exception {var f=RenderStateShard.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 static ClassNode node(String name)throws Exception {var n=new ClassNode();try(var in=Files.newInputStream(Path.of("build/classes/java/main/"+name+".class"))){new ClassReader(in).accept(n,0);}return n;}
 static void smokeContracts(OfflinePack pack,boolean nativeBuffers)throws Exception {
  var type=ShaderSmokeRenderer.type(ResourceLocation.fromNamespaceAndPath("flansmod","skins/effects/muzzlesmoke0.png"));
  var state=field(type,"state");
  check(type.format()==DefaultVertexFormat.BLOCK&&type.mode()==VertexFormat.Mode.QUADS,"terrain-translucent shader receives native block format");
  check(field(state,"shaderState")==shard("RENDERTYPE_TRANSLUCENT_SHADER"),"world transparent shader rather than solid entity fallback");
  check(field(state,"depthTestState")==shard("LEQUAL_DEPTH_TEST")&&field(state,"writeMaskState")==shard("COLOR_DEPTH_WRITE"),"native surface occlusion/depth reconstruction");
  check(field(state,"cullState")==shard("NO_CULL")&&(boolean)field(field(state,"textureState"),"blur"),"both authored sides, linear filtering");
  for(boolean hand:new boolean[]{false,true})for(boolean shaders:new boolean[]{false,true})
   check(ShaderSmokeRenderer.inWorld(hand,shaders)==(!hand||shaders),"exactly one smoke pass in first/third person with shaders on/off");
  var renderer=node("com/flansmod/client/render/MuzzleEffectsRenderer");boolean world=false,shade=false,lighting=false;
  for(var m:renderer.methods)for(var ins:m.instructions)if(ins instanceof MethodInsnNode c) {
   if(c.owner.endsWith("ShaderSmokeRenderer")&&c.name.equals("inWorld"))world=true;
   if(c.owner.endsWith("ShaderSmokeRenderer")&&c.name.equals("type"))shade=true;
   if(c.owner.endsWith("LevelRenderer")&&c.name.equals("getLightColor"))lighting=true;
  }
  check(world&&shade&&lighting,"production world-pass route, transparent shader and actual environment light");
  var seus=Path.of("run/shaderpacks/【光影】SEUS_PTGI_E12.zip");
  if(Files.exists(seus))try(var zip=new ZipFile(seus.toFile())) {
   var water=new String(zip.getInputStream(zip.getEntry("shaders/gbuffers_water.fsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   var data=new String(zip.getInputStream(zip.getEntry("shaders/lib/GBufferData.inc")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   check(water.contains("OutputGBufferDataTransparent")&&data.contains("PackTwo8BitTo16Bit(data.albedo.b, data.albedo.a)"),"installed SEUS world translucent path actually preserves texture alpha");
  }
  try(var in=VerifyShaderMuzzleLighting.class.getResourceAsStream("/net/irisshaders/iris/pipeline/programs/ShaderKey.class")) {
   if(in!=null) {var keys=new ClassNode();new ClassReader(in).accept(keys,0);boolean selected=false,water=false,noClip=false,found=false;
    for(var m:keys.methods)if(m.name.equals("<clinit>"))for(var ins:m.instructions) {
     if(ins instanceof LdcInsnNode l&&"TERRAIN_TRANSLUCENT".equals(l.cst)){selected=true;water=noClip=false;}
     if(selected&&ins instanceof FieldInsnNode f) {
      if(f.owner.endsWith("ProgramId")&&f.name.equals("Water"))water=true;
      if(f.owner.endsWith("AlphaTests")&&f.name.equals("OFF"))noClip=true;
      if(f.getOpcode()==Opcodes.PUTSTATIC&&f.name.equals("TERRAIN_TRANSLUCENT")){check(water&&noClip,"real Iris maps this shader to water/translucent without a hard alpha cutoff");found=true;selected=false;}
     }
    }check(found,"installed Iris terrain translucent mapping inspected");
   }
  }
  int meshes=0,vertices=0;
  if(nativeBuffers)for(var t:pack.types)if(t instanceof GunType gun&&gun.itsType!=GunType.GunItsType.MAG&&!gun.magazineNames.isEmpty())
   for(var spec:List.of(gun.muzzleSmoke,gun.chamberSmoke))if(spec.model!=null) {
    var rig=ModelCache.rigForReference(spec.model,1);var pose=new PoseStack();pose.translate(.2F,-.3F,-2F);
    try(var memory=new ByteBufferBuilder(4096)) {
     var buffer=new BufferBuilder(memory,VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
     RigRenderer.render(pose,buffer,rig,LightTexture.pack(4,10),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,1,1,1,.41F,false);
     try(var mesh=buffer.buildOrThrow()) {
      var bytes=mesh.vertexBuffer();int size=DefaultVertexFormat.BLOCK.getVertexSize();
      int colour=DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.COLOR);
      check(mesh.drawState().vertexCount()==rig.quads.size()*4&&bytes.remaining()==rig.quads.size()*4*size,"native smoke buffer complete with no missing overlay element");
      for(int i=0;i<rig.quads.size()*4;i++)check(Byte.toUnsignedInt(bytes.get(i*size+colour+3))==(int)(255*.41F),"actual serialized native vertex alpha preserved");
      vertices+=rig.quads.size()*4;meshes++;
     }
    }
   }
  System.out.printf("Smoke: transparent shader/depth/alpha/filtering and production phase/light contracts PASS; %d native CPU buffers, %d serialized smoke vertices (no GL).%n",meshes,vertices);
 }
 static void irisDispatch(GunType gun)throws Exception {
  Class<?> api;
  try{api=Class.forName("net.irisshaders.iris.api.v0.item.IrisItemLightProvider");}
  catch(ClassNotFoundException absent){System.out.println("Iris absent: common item still loads; API dispatch check skipped.");return;}
  // Model exactly the inheritance introduced by Iris's MixinItem: an interface default versus
  // ordinary methods on ItemFlan. A child class needs no forwarding methods or compile-time Iris import.
  var writer=new ClassWriter(0);String name="OfflineIrisFlanItem";
  writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,name,null,"com/flansmod/common/types/ItemFlan",new String[]{Type.getInternalName(api)});
  var m=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lcom/flansmod/common/types/InfoType;Lnet/minecraft/world/item/Item$Properties;)V",null,null);
  m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ALOAD,2);
  m.visitMethodInsn(Opcodes.INVOKESPECIAL,"com/flansmod/common/types/ItemFlan","<init>","(Lcom/flansmod/common/types/InfoType;Lnet/minecraft/world/item/Item$Properties;)V",false);
  m.visitInsn(Opcodes.RETURN);m.visitMaxs(3,3);m.visitEnd();writer.visitEnd();
  class Loader extends ClassLoader{Class<?> define(byte[] bytes){return defineClass(name,bytes,0,bytes.length);}}
  var item=new Loader().define(writer.toByteArray()).getConstructor(InfoType.class,Item.Properties.class).newInstance(gun,new Item.Properties());
  var player=net.minecraft.world.entity.player.Player.class;var stack=net.minecraft.world.item.ItemStack.class;
  check(api.isInstance(item),"real installed Iris interface implemented");
  check(item.getClass().getMethod("getLightEmission",player,stack).getDeclaringClass()==ItemFlan.class,"ordinary production method wins over real Iris default");
  check((int)api.getMethod("getLightEmission",player,stack).invoke(item,null,null)==0,"interface dispatch reaches dedicated-server/null-player guard");
  var colour=(Vector3f)api.getMethod("getLightColor",player,stack).invoke(item,null,null);
  check(colour.equals(new Vector3f(1F,.72F,.32F)),"real Iris default white replaced by production warm flash colour");
  System.out.println("Installed Iris API: inherited production light/colour methods override interface defaults without a required dependency.");
 }
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();int guns=0,enabled=0,lightSamples=0,frames=0,vertices=0;
  FmumShaderFlash.Frame saved=null;GunType first=null;
  for(var type:pack.types)if(type instanceof GunType gun&&gun.itsType!=GunType.GunItsType.MAG&&!gun.magazineNames.isEmpty()) {
   guns++;if(first==null)first=gun;
   var model=ModelCache.rigFor(gun,gun.modelScale).animation;var flash=FmumMuzzlePose.flash(gun,model);var shot=new FmumShotEffects();
   check(MuzzleLighting.level(gun,shot,.5F)==0,"idle has no environment light");
   shot.shoot(gun,flash,0,47,true);
   for(float partial:new float[]{0F,.2F,.5F,1F})check(shot.alpha(partial)==(flash.fade()>0?1:0),"confirmed shot lights first frame immediately");
   int previous=15;
   for(int tick=0;tick<16;tick++) {
    int level=MuzzleLighting.level(gun,shot,1);
    if(flash.model()==null||flash.scale()<=0||flash.fade()<=0)check(level==0,"pack-disabled flash emits no hand light: "+gun.shortName);
    else {check(level>=0&&level<=15&&level<=previous,"bounded decaying shader emission");if(tick==0)check(level==15,"actual firing pulse reaches maximum shader level");}
    previous=level;
    for(float partial:new float[]{0F,.2F,.5F,1F}) {int value=MuzzleLighting.level(gun,shot,partial);check(value>=0&&value<=15,"interpolated light bounds");lightSamples++;}
    shot.tick();
   }
   check(MuzzleLighting.level(gun,shot,1)==0,"pulse expires, no lingering light");shot.reset();check(MuzzleLighting.level(gun,shot,0)==0,"switch/logout removes light");
   if(flash.model()==null)continue;enabled++;
   var texture=Textures.of(flash.texture(0),false,p->true);
   var rig=ModelCache.rigForReference(flash.model(),1,texture);check(rig!=null&&!rig.quads.isEmpty(),"real pack flame geometry");
   for(float fov:new float[]{50,70,100})for(float aspect:new float[]{854F/480F,1920F/1080F})for(float ads:new float[]{0,1})
   for(float roll:new float[]{0,37,180})for(float yaw:new float[]{-1.5F,0,.87F}) {
    var projection=new Matrix4f().scale(1,1,.125F).mul(new Matrix4f().perspective((float)java.lang.Math.toRadians(fov),aspect,.05F,128F));
    var shaderView=new Matrix4f().rotateY(yaw).rotateX(.41F);
    var pose=new Matrix4f(shaderView).invert().translate(.56F,-.52F,-.72F);
    FmumFirstPersonPose.itemFrame(pose);FmumFirstPersonPose.placement(pose,model,ads,0,0);FmumFirstPersonPose.aim(pose,LegacySightPose.forGun(gun),ads);
    var frame=FmumShaderFlash.capture(rig,texture,projection,shaderView,pose,flash,roll,.75F);check(frame!=null,"enabled flame snapshot");
    var copy=new Matrix4f(projection);projection.identity();check(frame.projection().equals(copy,0),"projection copied before GUI changes it");
    var original=new Matrix4f(shaderView).mul(pose).mul(flash.pose(roll));
    var expected=new Matrix4f(original).scale(FmumShaderFlash.SIZE);
    check(frame.viewPose().getTranslation(new Vector3f()).distance(original.getTranslation(new Vector3f()))<.00002F,"resize about actual muzzle, never move anchor");
    var recorder=new Recorder();FmumShaderFlash.emit(recorder,frame);check(recorder.vertices.size()==rig.quads.size()*4,"same original mesh, no replacement sprite");
    int i=0;
    for(var quad:rig.quads)for(int j=0;j<4;j++) {
     var v=recorder.vertices.get(i++);var p=quad.positions()[j];var uv=quad.uvs()[j];
     var oldClip=copy.transform(expected.transform(new Vector4f(p[0],p[1],p[2],1)));
     var newClip=frame.projection().transform(new Vector4f(v.point(),1));
     check(oldClip.distance(newClip)<.0003F,"native perspective and camera pose exactly preserved: "+gun.shortName);
     check(v.u()==uv[0]&&v.v()==uv[1]&&v.alpha()==(int)(.75F*FmumShaderFlash.OPACITY*255),"original UV and proportional decay preserved");vertices++;
    }
    check(java.lang.Math.abs(frame.glow().pose().m02())<.00001F&&java.lang.Math.abs(frame.glow().pose().m12())<.00001F,"halo remains view-facing after composition");
    var queue=new FmumShaderFlash.Frames();queue.put(false,frame);queue.put(false,frame);queue.put(true,frame);
    check(queue.take().size()==2&&queue.take().isEmpty(),"one draw per hand, consumed once even with two Iris hand passes");
    queue.put(false,frame);queue.clear();check(queue.take().isEmpty(),"frame boundary clears stale flash on GUI hide/switch");
    saved=frame;frames++;
   }
  }
  check(guns==31&&enabled==29,"all 31 EFT guns, ASVAL/VSS exclusions");
  smokeContracts(pack,args.length>1&&args[1].equals("--native-smoke"));
  ((net.minecraft.core.MappedRegistry<?>)net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
  var common=new ItemFlan(first,new Item.Properties());check(common.getLightEmission(null,null)==0,"common item loads without Iris/client singleton");
  check(common.getLightColor(null,null).equals(new Vector3f(1F,.72F,.32F)),"warm colour independent of client singleton");irisDispatch(first);
  var nativeType=ShaderMuzzleRenderer.flashType(saved.texture());var nativeState=field(nativeType,"state");
  check(nativeType.format()==DefaultVertexFormat.POSITION_TEX_COLOR&&nativeType.mode()==VertexFormat.Mode.QUADS,"native textured unlit shader format");
  check(field(nativeState,"transparencyState")==shard("TRANSLUCENT_TRANSPARENCY"),"preserve flame's actual alpha blend");
  check(field(nativeState,"writeMaskState")==shard("COLOR_WRITE")&&field(nativeState,"cullState")==shard("NO_CULL"),"no depth write; preserve both sides' distinct authored artwork");
  check((boolean)field(field(nativeState,"textureState"),"blur"),"bilinear effect filtering softens enlarged texels");
  var mirror=new FmumShaderFlash.Frame(saved.rig(),saved.texture(),saved.projection(),new Matrix4f(saved.viewPose()).scale(-1,1,1),saved.alpha(),saved.glow());
  var mirrored=new Recorder();FmumShaderFlash.emit(mirrored,mirror);var firstQuad=saved.rig().quads.getFirst();
  for(int i=0;i<4;i++) {
   var expectedPoint=mirror.viewPose().transformPosition(new Vector3f(firstQuad.positions()[i]));
   check(expectedPoint.distance(mirrored.vertices.get(i).point())<.00001F,"left-hand reflection preserves original mesh");
   check(mirrored.vertices.get(i).u()==firstQuad.uvs()[i][0],"mirrored UV stays with its vertex");
  }
  var commonNode=node("com/flansmod/common/types/ItemFlan");check(commonNode.interfaces.isEmpty(),"no hard Iris interface/dependency in common production item");
  var renderer=node("com/flansmod/client/render/ShaderMuzzleRenderer");
  var begin=renderer.methods.stream().filter(m->m.name.equals("beginFrame")).findFirst().orElseThrow();
  var draw=renderer.methods.stream().filter(m->m.name.equals("afterScene")).findFirst().orElseThrow();
  check(begin.desc.contains("RenderFrameEvent$Pre")&&draw.desc.contains("RenderGuiEvent$Pre"),"after-composition drawing preceded by frame-boundary cleanup");
  boolean captured=false,emitted=false,depth=false,restore=false;
  for(var m:node("com/flansmod/client/render/MuzzleEffectsRenderer").methods)for(var ins:m.instructions)
   if(ins instanceof MethodInsnNode call&&call.owner.endsWith("FmumShaderFlash")&&call.name.equals("capture"))captured=true;
  for(var ins:draw.instructions)if(ins instanceof MethodInsnNode call) {
   if(call.owner.endsWith("FmumShaderFlash")&&call.name.equals("emit"))emitted=true;
   if(call.name.equals("disableDepthTest"))depth=true;if(call.name.equals("popMatrix"))restore=true;
  }
  check(captured&&emitted&&depth&&restore,"production captures real hand mesh, draws after scene and restores native matrix/depth state");
  System.out.printf("PASS: %d EFT guns (%d flash-enabled), %d light samples, %d hand projection/ADS/FOV/camera/roll snapshots and %d original mesh vertices; original UV/alpha, optional API checks above, no hard dependency, one-frame/two-hand lifecycle and native post-composite render state. GPU/shader appearance requires manual acceptance.%n",guns,enabled,lightSamples,frames,vertices);
 }
}
