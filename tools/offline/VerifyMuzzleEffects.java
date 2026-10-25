import com.flansmod.client.model.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Actual core bytecode/real smoke execution, all EFT effect assets and independent mount fixtures. */
public final class VerifyMuzzleEffects implements Opcodes {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void close(float a,float b,String s){check(Math.abs(a-b)<.00002F,s+": "+a+" vs "+b);}
 public static void main(String[] args)throws Exception {
  core();vectorMutation();
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
  int guns=0,textures=0,models=0;
  for(var t:pack.types)if(t instanceof GunType g) {
   var rig=ModelCache.rigFor(g,g.modelScale);if(rig==null)continue;
   var flash=FmumMuzzlePose.flash(g,rig.animation);
   if(g.itsType==GunType.GunItsType.MAG){check(flash==null,"magazine never flashes");continue;}
   if(g.magazineNames.isEmpty())continue; // the other three GunType definitions are melee weapons
   guns++;check(flash!=null && flash.fade()>0 && flash.fade()<1,"active flash: "+g.shortName);
   check(flash.pose(37).isFinite(),"finite flash placement: "+g.shortName);
   if(flash.model()!=null) {
    models+=model(flash.model());
    for(int i=0;i<flash.textures();i++)textures+=texture(pack,flash.texture(i));
   }
   var state=new FmumShotEffects();check(state.alpha(1)==0 && state.takeSmoke(g)==0,"idle has no flash or smoke");
   state.shoot(g,flash,0,37,true);check(state.takeSmoke(g)==1 && state.takeSmoke(g)==0,"one smoke emission per shot, regardless of render frames");
   for(int i=1;i<=12;i++){state.tick();close(state.alpha(1),(float)Math.pow(flash.fade(),i),g.shortName+" core flash decay "+i);}
   state.shoot(g,flash,0,37,true);for(int i=0;i<5;i++)state.tick();check(state.takeSmoke(g)==0,"hidden stale shot expires");
   state.reset();check(state.gun==null && state.alpha(1)==0 && state.takeSmoke(g)==0,"switch/logout clears muzzle state");
   for(boolean chamber:new boolean[]{false,true}) {
    var spec=chamber?g.chamberSmoke:g.muzzleSmoke;
    check(FmumMuzzlePose.smoke(g,rig.animation,chamber).isFinite(),"finite smoke mount: "+g.shortName);
    if(spec.model==null || spec.vanishConst<=0)continue;
    models+=model(spec.model);for(int i=0;i<spec.numTexture;i++)textures+=texture(pack,spec.texture+i);
   }
   System.out.printf("%s flash=%s model=%s textures=%d fade=%.6f scale=%.4f origin=%s muzzleSmoke=%s chamberSmoke=%s%n",g.shortName,flash.anchor(),flash.model(),flash.textures(),flash.fade(),flash.scale(),flash.mount().getTranslation(new Vector3f()),g.muzzleSmoke.model,g.chamberSmoke.model);
  }
  var ak=(GunType)pack.types.stream().filter(t->"AK74M".equals(t.shortName)).findFirst().orElseThrow();
  var brake=AttachmentType.getAttachment("6P20AK74MuzzleBrake");
  close(brake.muzzleFlashVanishConst,1F,"ordinary muzzle attachment leaves flash enabled");check(brake.numFlashTexture==0,"ordinary attachment inherits gun texture count");
  var am=ModelCache.rigFor(ak,ak.modelScale).animation;var flash=FmumMuzzlePose.flash(ak,am);
  close(flash.mount().m30(),1.25F+1.375F*.2F,"AK74M raw default mount + scaled Vector3f.set brake point");close(flash.mount().m31(),.2875F,"AK74M brake height");
  close(flash.scale(),.5F,"flash drawn outside receiver ModelScale .2");
  close(ak.muzzleSmoke.scale,.1F,"core post-read final muzzle smoke size");close(ak.muzzleSmoke.scaleEx,.05F,"core initial minus final muzzle size");
  close(ak.muzzleSmoke.freeRotate,1F,"omitted gun smoke rotation flag inherits core true");
  close(ak.chamberSmoke.scale,.15F,"core post-read final chamber size");close(ak.chamberSmoke.scaleEx,-.05F,"core chamber smoke grows while fading");
  nested();stationary();wire();
  check(guns==31,"31 EFT firearms audited");
  System.out.printf("PASS: actual core default/fade/numbered-texture contracts, original GunSmoke execution, %d/31 firearms, %d model references, %d texture variants, shot-only lifetime, reset/expiry, nested rotation and muzzle smoke scale, stationary camera/world conversion, server observer wiring.%n",guns,models,textures);
 }
 static int model(String ref){var rig=ModelCache.rigForReference(ref,1);check(rig!=null && !rig.quads.isEmpty(),"effect mesh exists: "+ref);return 1;}
 static int texture(OfflinePack p,String name)throws Exception {
  var id=Textures.of(name,false,p::has);check(id!=null && p.has(id),"effect texture exists: "+name);
  var image=ImageIO.read(p.path(id).toFile());check(image!=null,"PNG decodes: "+name);
  boolean visible=false;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>25)visible=true;
  if(!visible)System.out.println("Pack-designed transparent effect variant: "+name);
  return 1;
 }
 static void nested() {
  var base=new GunType(new TypeFile(EnumType.gun,"effect_fixture"));base.flashModel="EFTRDef.EftMuzzleFlash";base.flashTexture="effects/MuzzleFlash/Round";base.muzzleFlashScale=.5F;base.muzzleFlashVanishConst=.25F;
  var barrel=new AttachmentType(new TypeFile(EnumType.attachment,"effect_barrel"));barrel.shortName="effect_barrel";barrel.itsType="barrel";barrel.modelString="EFTRWea.6P20AK74MuzzleBrake";barrel.modelScale=.2F;barrel.muzzleFlashVanishConst=.5F;
  var muzzle=new AttachmentType(new TypeFile(EnumType.attachment,"effect_muzzle"));muzzle.shortName="effect_muzzle";muzzle.itsType="muzzle";muzzle.modelString=barrel.modelString;muzzle.modelScale=.2F;muzzle.muzzleFlashVanishConst=.4F;muzzle.numFlashTexture=4;muzzle.flashModelName=base.flashModel;muzzle.flashTextureName="effects/fixture";muzzle.muzzleSmoke.at=.03F;
  // A suppressor authors its own FlashModel with scale 0; that must remove the flash entirely.
  var quiet=new AttachmentType(new TypeFile(EnumType.attachment,"effect_quiet"));
  quiet.shortName="effect_quiet";quiet.itsType="muzzle";quiet.modelString=barrel.modelString;quiet.modelScale=.22F;
  quiet.flashModelName=base.flashModel;quiet.flashTextureName="effects/suppressed";quiet.muzzleFlashScale=0F;quiet.muzzleFlashVanishConst=.2F;
  var quietBase=new GunType(new TypeFile(EnumType.gun,"effect_quiet_fixture"));
  quietBase.flashModel="EFTRDef.EftMuzzleFlash";quietBase.flashTexture="effects/MuzzleFlash/Round";quietBase.muzzleFlashScale=.5F;quietBase.muzzleFlashVanishConst=.25F;
  var qs=new ModifiableType.AttachableSlot();qs.x=.3F;qs.y=.2F;qs.rotate=90;quietBase.slots=new ModifiableType.AttachableSlot[]{qs};
  var qn=new ModifiableType.DefaultAttachment();qn.name=quiet.shortName;qn.slot=0;qn.pos=.02F;quietBase.defaultAttachments.add(List.of(qn));
  check(FmumMuzzlePose.flash(quietBase,new ModelGeometry.Model())==null,"attachment flash scale 0 removes the flash (suppressor)");
  quiet.muzzleFlashScale=.2F;
  var dim=FmumMuzzlePose.flash(quietBase,new ModelGeometry.Model());
  check(dim!=null,"a non-zero attachment flash scale keeps a smaller flash");
  close(dim.scale(),.2F,"attachment flash scale replaces the gun's scale");
  var a=new ModifiableType.AttachableSlot();a.x=.3F;a.y=.2F;a.rotate=90;base.slots=new ModifiableType.AttachableSlot[]{a};
  var b=new ModifiableType.AttachableSlot();b.x=.4F;b.y=.1F;b.rotate=20;barrel.slots=new ModifiableType.AttachableSlot[]{b};
  var n=new ModifiableType.DefaultAttachment();n.name=barrel.shortName;n.slot=0;n.pos=.05F;
  var c=new ModifiableType.DefaultAttachment();c.name=muzzle.shortName;c.slot=0;c.pos=.02F;n.children.add(c);base.defaultAttachments.add(List.of(n));
  var m=ModelCache.rigForReference(barrel.modelString,1).animation;var point=m.fmumPose.get("muzzleFlashPoint");
  var expected=new Matrix4f().translate(.35F,.2F,0).rotateX((float)Math.toRadians(90)).translate(.42F,.1F,0).rotateX((float)Math.toRadians(20)).translate(point[0]*.2F,point[1]*.2F,point[2]*.2F);
  var got=FmumMuzzlePose.flash(base,new ModelGeometry.Model());check(got.mount().equals(expected,.00002F),"nested child-first flash mount with accumulated rotation and correct scale");
  check(got.anchor().equals(muzzle.shortName) && got.texture(6).equals("effects/fixture_2"),"outer muzzle selects texture model/count");close(got.fade(),.25F*.5F*.4F,"all attachment fade contributions multiply");
  close(FmumMuzzlePose.smoke(base,new ModelGeometry.Model(),false).m30(),.8F,"smoke mount X includes nested slots and attachment smoke distance once");
 }
 static void stationary() {
  var camera=new Matrix4f().translation(100,64,-50).rotateY(.7F).rotateX(-.3F);
  var view=new Matrix4f().translate(.56F,-.52F,-.72F).rotateY(.6F).scale(.4F).translate(1.25F,.2875F,0);
  var world=new Matrix4f(camera).mul(view);var smoke=new FmumSmoke("m","t",FmumMuzzlePose.smokeOrigin(world,37),.15F,.1F,.8F);
  var initial=smoke.origin.getTranslation(new Vector3f());
  for(int i=0;i<5;i++)smoke.tick();check(smoke.origin.getTranslation(new Vector3f()).equals(initial),"smoke never follows later camera/gun movement");
  close(smoke.alpha,(float)Math.pow(.8,5),"smoke multiplicative fade");close(smoke.scale(),.15F+.1F*smoke.alpha,"actual core alpha-dependent scale");
  var recovered=new Matrix4f(camera).invert().mul(world);check(recovered.equals(view,.00002F),"inverse camera and view frame recover exact muzzle placement");
  var direction=world.transformDirection(new Vector3f(1,0,0)).normalize();
  check(smoke.origin.transformDirection(new Vector3f(1,0,0)).normalize().equals(direction,.00002F),"smoke yaw/pitch retains barrel direction and strips hand scale");
  while(smoke.alive())smoke.tick();check(smoke.age<30,"smoke eventually removed");
 }
 static ClassNode read(ZipFile zip,String cls)throws Exception {var n=new ClassNode();try(var in=zip.getInputStream(zip.getEntry(cls+".class"))){new ClassReader(in).accept(n,0);}return n;}
 static void vectorMutation()throws Exception {
  var cw=new ClassWriter(0);cw.visit(V1_8,ACC_PUBLIC,"ModelEffectFixture",null,"com/flansmod/client/model/ModelGun",null);
  var method=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);method.visitCode();
  method.visitVarInsn(ALOAD,0);method.visitFieldInsn(GETFIELD,"ModelEffectFixture","muzzleFlashPoint","Lcom/flansmod/common/vector/Vector3f;");
  method.visitLdcInsn(1.375F);method.visitLdcInsn(.2F);method.visitLdcInsn(-.1F);method.visitMethodInsn(INVOKEVIRTUAL,"com/flansmod/common/vector/Vector3f","set","(FFF)V",false);
  method.visitVarInsn(ALOAD,0);method.visitFieldInsn(GETFIELD,"ModelEffectFixture","barrelOrigin","Lcom/flansmod/common/vector/Vector3f;");
  method.visitLdcInsn(.4F);method.visitMethodInsn(INVOKEVIRTUAL,"com/flansmod/common/vector/Vector3f","set","(F)V",false);
  method.visitVarInsn(ALOAD,0);method.visitFieldInsn(GETFIELD,"ModelEffectFixture","chamberSmokeOri","Lcom/flansmod/common/vector/Vector3f;");
  method.visitLdcInsn(-.125F);method.visitFieldInsn(PUTFIELD,"com/flansmod/common/vector/Vector3f","z","F");
  method.visitInsn(RETURN);method.visitMaxs(5,1);method.visitEnd();cw.visitEnd();
  var m=BytecodeModel.fromStream(new java.io.ByteArrayInputStream(cw.toByteArray()));
  check(Arrays.equals(m.fmumPose.get("muzzleFlashPoint"),new float[]{1.375F,.2F,-.1F}),"Vector3f.set triple extracted");
  check(Arrays.equals(m.fmumPose.get("barrelOrigin"),new float[]{.4F,.4F,.4F}),"Vector3f.set scalar extracted");
  check(Arrays.equals(m.fmumPose.get("chamberSmokeOri"),new float[]{0,0,-.125F}),"Vector3f component mutation extracted");
 }
 static void core()throws Exception {
  try(var zip=new ZipFile("build/eft-core-reference.jar")) {
   var at=read(zip,"com/flansmod/common/guns/AttachmentType");
   for(String field:List.of("muzzleFlashVanishConst","muzzleFlashScale")) {
    boolean found=false;for(var method:at.methods)if(method.name.equals("<init>"))for(var insn:method.instructions)
     if(insn instanceof FieldInsnNode f && f.getOpcode()==PUTFIELD && f.name.equals(field) && f.getPrevious().getOpcode()==FCONST_1)found=true;
    check(found,"actual core attachment default 1: "+field);
   }
   var anim=read(zip,"com/flansmod/client/model/GunAnimations");boolean tick=false;
   for(var method:anim.methods)if(method.name.equals("update"))for(var insn:method.instructions)
    if(insn instanceof FieldInsnNode f && f.name.equals("flashAlpha") && f.getOpcode()==PUTFIELD && f.getPrevious().getOpcode()==FMUL)tick=true;
   check(tick,"actual core multiplies flash alpha each update");
   var gt=read(zip,"com/flansmod/common/guns/GunType");boolean smokeRotate=false;
   for(var method:gt.methods)if(method.name.equals("<init>"))for(var insn:method.instructions)
    if(insn instanceof FieldInsnNode f && f.getOpcode()==PUTFIELD && f.name.equals("muzzleSmokeFreeRotate") && f.getPrevious().getOpcode()==ICONST_1)smokeRotate=true;
   check(smokeRotate,"actual core muzzle smoke rotation defaults true");
   var handler=read(zip,"com/flansmod/client/FlansModResourceHandler");boolean underscore=false;
   for(var method:handler.methods)if(method.name.equals("getnumedTexture"))for(var insn:method.instructions)if(insn instanceof LdcInsnNode ldc && "_".equals(ldc.cst))underscore=true;
   check(underscore,"actual flash file numbering uses underscore");
  }
  // Execute the real 1.7.10 GunSmoke without launching Minecraft. Only ModelDefault and
  // FlansMod.numScopeTextures are replaced; original vector arithmetic and GunSmoke run unchanged.
  try(var zip=new ZipFile("build/eft-core-reference.jar")) {
   var loader=new ClassLoader(VerifyMuzzleEffects.class.getClassLoader()) {
    @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
     if(!name.equals("com.flansmod.client.model.GunSmoke") && !name.equals("com.flansmod.client.model.ModelDefault") && !name.equals("com.flansmod.common.FlansMod") && !name.equals("net.minecraft.util.Vec3") && !name.startsWith("com.flansmod.common.vector."))return super.loadClass(name,resolve);
     var loaded=findLoadedClass(name);if(loaded!=null)return loaded;
     try {
      byte[] bytes;
      if(name.endsWith("ModelDefault") || name.equals("com.flansmod.common.FlansMod") || name.equals("net.minecraft.util.Vec3")) {
       var cw=new ClassWriter(0);cw.visit(V1_8,ACC_PUBLIC,name.replace('.','/'),null,"java/lang/Object",null);
       if(name.endsWith("FlansMod"))cw.visitField(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"numScopeTextures","I",null,3).visitEnd();
       cw.visitEnd();bytes=cw.toByteArray();
      }else try(var in=zip.getInputStream(zip.getEntry(name.replace('.','/')+".class"))){bytes=in.readAllBytes();}
      var cls=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(cls);return cls;
     }catch(Exception e){throw new ClassNotFoundException(name,e);}
    }
   };
   var real=loader.loadClass("com.flansmod.client.model.GunSmoke");var vector=loader.loadClass("com.flansmod.common.vector.Vector3f");
   var object=real.getConstructor().newInstance();var pos=vector.getConstructor(float.class,float.class,float.class).newInstance(1F,2F,3F);
   real.getMethod("set",loader.loadClass("com.flansmod.client.model.ModelDefault"),String.class,float.class,float.class,vector,float.class,float.class,float.class,float.class).invoke(object,null,"smoke0",.15F,.1F,pos,37F,42F,53F,.8F);
   var ours=new FmumSmoke("model","smoke0",new Matrix4f(),.15F,.1F,.8F);
   for(int i=0;i<25;i++) {
    close(ours.alpha,real.getField("alpha").getFloat(object),"executed original smoke alpha tick "+i);
    close(ours.scale(),(Float)real.getMethod("getScale").invoke(object),"executed original smoke scale tick "+i);
    real.getMethod("update",float.class,float.class,float.class).invoke(object,-.2F,0F,.1F);ours.tick();
   }
   System.out.println("Original EFT GunSmoke executed: 25 decay/scale steps match.");
  }
 }
 static void wire()throws Exception {
  var firing=Files.readString(Path.of("src/main/java/com/flansmod/common/GunFiring.java")).replace("\r\n","\n");
  check(firing.contains("if(!level.isClientSide && loadedDefinition != null)\n\t\t{\n\t\t\tnet.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,"),"flash only broadcast once by successful server shots");
  var driver=Files.readString(Path.of("src/main/java/com/flansmod/client/GunAnimationDriver.java"));
  check(!driver.contains("MuzzleEffectsRenderer.shoot("),"prediction does not duplicate server-confirmed flash/smoke");
  var packet=new com.flansmod.common.network.GunShotPayload(42,"AK74M",false,123456789L);
  var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try {
   com.flansmod.common.network.GunShotPayload.STREAM_CODEC.encode(buf,packet);
   check(packet.equals(com.flansmod.common.network.GunShotPayload.STREAM_CODEC.decode(buf)) && !buf.isReadable(),"real network codec round trip, including shared effect seed");
  }finally{buf.release();}
  var registry=Files.readString(Path.of("src/main/java/com/flansmod/client/GunAnimationRegistry.java"));
  check(registry.contains("MuzzleEffectsRenderer.reset();") && registry.contains("MuzzleEffectsRenderer.tick();"),"world cleanup and single tick hook");
 }
}
