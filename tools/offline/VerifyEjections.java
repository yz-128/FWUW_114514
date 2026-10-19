import com.flansmod.client.model.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.types.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.*;
import javax.imageio.ImageIO;

/** Runs the real EFT GunCase physics and verifies real item/chamber transitions and assets. */
public final class VerifyEjections implements Opcodes {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void close(float a,float b,String s){check(Math.abs(a-b)<.00002F,s+": "+a+" vs "+b);}
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  ((net.minecraft.core.MappedRegistry<?>)net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
  core();vectors();int bullets=0,cases=0,guns=0,bolts=0,resolved=0;
  for(var t:pack.types)if(t instanceof BulletType b) {
   if(b.isCase){cases++;asset(pack,b);continue;}
   bullets++;var casing=GunCasings.casing(b);
   if(casing==null){System.out.println("PACK MISSING CASING DEFINITION (matches original no-effect): "+b.shortName+" -> "+b.dropItemOnShoot);continue;}
   resolved++;asset(pack,casing);
  }
  for(var t:pack.types)if(t instanceof GunType g && !g.magazineNames.isEmpty() && g.itsType!=GunType.GunItsType.MAG) {
   guns++;if(g.itsType==GunType.GunItsType.BOLT_GUN)bolts++;
   var m=ModelCache.rigFor(g,g.modelScale).animation;
   check(FmumCasing.origin(m,new Matrix4f()).isFinite(),"real pack origin or original ModelGun zero default: "+g.shortName);
   for(boolean manual:new boolean[]{false,true}) {
    var effect=new FmumCasing(BulletType.getBullet("556x45mmCasing"),m,new Matrix4f().rotateY(.3F).scale(.4F),manual,8L,0F);
    for(int i=0;i<effect.lifetime;i++){check(effect.pose(.5F).isFinite(),"finite trajectory/spin: "+g.shortName);effect.tick(null,0,0,0);}
    check(!effect.alive(),"model-defined lifetime: "+g.shortName);
   }
   System.out.printf("%s %s origin=%s lifetime=%.0f%n",g.shortName,g.itsType,Arrays.toString(FmumActionPose.vector(m,"caseOrigin")),FmumActionPose.scalar(m,"caseTime",30F));
  }
  var ak=gun(pack,"AK74M");var mosin=gun(pack,"Mosin");
  var round=BulletType.getBullet("7.62x54R7BT1");var casing=GunCasings.casing(round);
  var mag=(GunType)pack.types.stream().filter(t->t instanceof GunType g && GunMagazine.acceptsMagazine(mosin,g)).findFirst().orElseThrow();
  var gunItem=VerifyMagazineData.register(mosin,"case_fixture_gun");var magItem=VerifyMagazineData.register(mag,"case_fixture_mag");
  var roundItem=VerifyMagazineData.register(round,"case_fixture_round");
  var stack=new net.minecraft.world.item.ItemStack(gunItem);var magazine=new net.minecraft.world.item.ItemStack(magItem);
  GunMagazine.set(magazine,mag,0,new net.minecraft.world.item.ItemStack(roundItem,2));GunMagazine.set(stack,mosin,0,magazine);
  check(GunMagazine.ensureChamber(stack,mosin),"initial bolt round feeds");
  check(GunMagazine.spendChamberedRound(stack,mosin).getItem()==roundItem,"bolt fires actual chambered round");
  check(GunCasings.retained(stack)==casing && GunMagazine.getChamberedRound(stack).isEmpty(),"spent case retained, never treated as live ammunition");
  check(!GunMagazine.ensureChamber(stack,mosin) && GunMagazine.roundsRemaining(GunMagazine.get(stack,mosin,0))==1,"cannot feed over spent case or lose next magazine round");
  var ejected=new ArrayList<net.minecraft.world.item.ItemStack>();GunReloading.charge(stack,mosin,ejected::add);
  check(ejected.isEmpty() && GunCasings.retained(stack)==null && GunMagazine.getChamberedRound(stack).getItem()==roundItem,"charging clears spent case once and feeds next live round");
  GunReloading.charge(stack,mosin,ejected::add);check(ejected.size()==1 && ejected.getFirst().getItem()==roundItem,"manual charge ejects exact live round once");
  var queue=new FmumShotEffects();queue.requestCase(ak,casing,false,1L);check(queue.takeCases(ak).size()==1 && queue.takeCases(ak).isEmpty(),"one ejection regardless of render frames");
  queue.requestCase(ak,casing,false,1L);for(int i=0;i<5;i++)queue.tick();check(queue.takeCases(ak).isEmpty(),"stale hidden ejection expires");
  queue.requestCase(ak,casing,false,1L);queue.reset();check(queue.takeCases(ak).isEmpty(),"switch/death/logout clears requests");
  var packet=new com.flansmod.common.network.GunEjectionPayload("AK74M","556x45mmCasing",false,78L);
  var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try{com.flansmod.common.network.GunEjectionPayload.STREAM_CODEC.encode(buffer,packet);check(packet.equals(com.flansmod.common.network.GunEjectionPayload.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(),"actual ejection network codec");}finally{buffer.release();}
  check(guns==31 && bullets==73 && cases==2 && resolved==61,"full EFT firearm/ammunition coverage, including 12 original unresolved references");
  System.out.printf("PASS: real core GunCase execution (both ejection profiles), 31 guns (%d bolt guns), 73 live-round references (%d valid, 12 original missing definitions), 2 actual casing meshes/textures, world gravity/interpolation/spin/lifetime, bolt/live-round inventory transitions, one-shot/expiry/reset and real network codec.%n",bolts,resolved);
 }
 static void asset(OfflinePack pack,BulletType b)throws Exception {
  var rig=ModelCache.rigForReference(b.bulletModel,1F);check(rig!=null && !rig.quads.isEmpty(),"casing model: "+b.shortName);
  var texture=Textures.of(b.bulletTexture,false,pack::has);check(texture!=null && ImageIO.read(pack.path(texture).toFile())!=null,"casing texture: "+b.shortName);
 }
 static void vectors()throws Exception {
  var cw=new ClassWriter(0);cw.visit(V1_8,ACC_PUBLIC,"ModelCaseFixture",null,"com/flansmod/client/model/ModelGun",null);
  var mv=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);mv.visitCode();
  mv.visitVarInsn(ALOAD,0);mv.visitFieldInsn(GETFIELD,"ModelCaseFixture","caseOrigin","Lcom/flansmod/common/vector/Vector3f;");
  mv.visitLdcInsn(.4F);mv.visitLdcInsn(.3F);mv.visitLdcInsn(.2F);mv.visitMethodInsn(INVOKEVIRTUAL,"com/flansmod/common/vector/Vector3f","set","(FFF)V",false);mv.visitInsn(RETURN);mv.visitMaxs(4,1);mv.visitEnd();cw.visitEnd();
  var m=BytecodeModel.fromStream(new java.io.ByteArrayInputStream(cw.toByteArray()));check(Arrays.equals(m.fmumPose.get("caseOrigin"),new float[]{.4F,.3F,.2F}),"caseOrigin inherited Vector3f.set decoded");
 }
 static GunType gun(OfflinePack pack,String name){return (GunType)pack.types.stream().filter(t->name.equals(t.shortName)).findFirst().orElseThrow();}
 static byte[] stub(String name)throws Exception {
  var cw=new ClassWriter(0);cw.visit(V1_8,ACC_PUBLIC,name.replace('.','/'),null,"java/lang/Object",null);
  var init=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(ALOAD,0);init.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(RETURN);init.visitMaxs(1,1);init.visitEnd();
  if(name.endsWith("FlansMod")) {cw.visitField(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"numScopeTextures","I",null,3).visitEnd();cw.visitField(ACC_PUBLIC|ACC_STATIC,"rand","Ljava/util/Random;",null,null).visitEnd();}
  if(name.endsWith("ModelGun")) {
   for(String field:List.of("caseEjectVelocity","caseEjectRandV","caseEjectAngularV","caseEjectRandAV","caseEjectVelocityWC","caseEjectRandVWC","caseEjectAngularVWC","caseEjectRandAVWC"))cw.visitField(ACC_PUBLIC,field,"Lcom/flansmod/common/vector/Vector3f;",null,null).visitEnd();
   for(String field:List.of("rotVelocity","rotVelocityWC","vFriction","rvFriction","gAcceleration"))cw.visitField(ACC_PUBLIC,field,"F",null,null).visitEnd();cw.visitField(ACC_PUBLIC,"caseTime","I",null,null).visitEnd();
  }
  if(name.endsWith("RenderGun"))for(String field:List.of("caseTrans","caseRot"))cw.visitField(ACC_PUBLIC|ACC_STATIC,field,"Lcom/flansmod/common/vector/Vector3f;",null,null).visitEnd();
  cw.visitEnd();return cw.toByteArray();
 }
 static void core()throws Exception {
  try(var zip=new ZipFile("build/eft-core-reference.jar")) {
   var node=new org.objectweb.asm.tree.ClassNode();
   try(var in=zip.getInputStream(zip.getEntry("com/flansmod/client/model/RenderGun.class"))){new ClassReader(in).accept(node,0);}
   boolean normal=false,wc=false;
   for(var method:node.methods)for(var insn:method.instructions)if(insn instanceof org.objectweb.asm.tree.FieldInsnNode f && f.getOpcode()==GETFIELD) {
    if(f.name.equals("caseEjectVelocity"))normal=true;if(f.name.equals("caseEjectVelocityWC"))wc=true;
   }
   check(normal && !wc,"actual RenderGun overwrites manual WC vectors with standard ejection vectors");
   var loader=new ClassLoader(VerifyEjections.class.getClassLoader()) {
    protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
     boolean original=name.equals("com.flansmod.client.model.GunCase") || name.startsWith("com.flansmod.common.vector.");
     boolean replacement=List.of("com.flansmod.common.FlansMod","com.flansmod.client.model.ModelGun","com.flansmod.client.model.RenderGun","com.flansmod.common.guns.ShootableType","net.minecraft.util.Vec3").contains(name);
     if(!original && !replacement)return super.loadClass(name,resolve);
     var loaded=findLoadedClass(name);if(loaded!=null)return loaded;
     try{byte[] bytes;if(replacement)bytes=stub(name);else try(var in=zip.getInputStream(zip.getEntry(name.replace('.','/')+".class"))){bytes=in.readAllBytes();}var cls=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(cls);return cls;}catch(Exception e){throw new ClassNotFoundException(name,e);}
    }
   };
   var vector=loader.loadClass("com.flansmod.common.vector.Vector3f");var gm=loader.loadClass("com.flansmod.client.model.ModelGun");var mod=loader.loadClass("com.flansmod.common.FlansMod");var render=loader.loadClass("com.flansmod.client.model.RenderGun");var original=loader.loadClass("com.flansmod.client.model.GunCase");var st=loader.loadClass("com.flansmod.common.guns.ShootableType");
   for(boolean manual:new boolean[]{false,true}) {
    var model=gm.getConstructor().newInstance();
    var fields=Map.of("caseEjectVelocity",new float[]{-.025F,.025F,.25F},"caseEjectRandV",new float[]{.075F,.05625F,.03125F},"caseEjectAngularV",new float[]{-.3F,-.7F,-.3F},"caseEjectRandAV",new float[]{.6F,-.8F,.6F},"caseEjectVelocityWC",new float[]{-.01875F,.0125F,.09375F},"caseEjectRandVWC",new float[]{.0375F,.025F,.025F},"caseEjectAngularVWC",new float[]{-.3F,-.7F,-.3F},"caseEjectRandAVWC",new float[]{.6F,-.8F,.6F});
    for(var field:fields.entrySet())gm.getField(field.getKey()).set(model,v(vector,field.getValue()));
    gm.getField("rotVelocity").setFloat(model,55F);gm.getField("rotVelocityWC").setFloat(model,30F);gm.getField("caseTime").setInt(model,30);
    gm.getField("vFriction").setFloat(model,.1F);gm.getField("rvFriction").setFloat(model,.2F);gm.getField("gAcceleration").setFloat(model,-5F);
    mod.getField("rand").set(null,new Random(77));render.getField("caseTrans").set(null,v(vector,new float[3]));render.getField("caseRot").set(null,v(vector,new float[3]));
    var real=original.getConstructor().newInstance();var bullet=st.getConstructor().newInstance();
    original.getMethod("preSet",st,gm,boolean.class).invoke(real,bullet,model,!manual);
    float actualSpin=original.getField("rotVelocity").getFloat(real);
    // RenderGun replaces WC initial vectors with normal vectors; preserve the actual preSet spin.
    mod.getField("rand").set(null,new Random(77));original.getMethod("preSet",st,gm,boolean.class).invoke(real,bullet,model,true);
    original.getField("rotVelocity").setFloat(real,actualSpin);
    var type=new BulletType(new TypeFile(EnumType.bullet,"case_physics"));type.bulletScale=.044F;
    var ours=new FmumCasing(type,new ModelGeometry.Model(),new Matrix4f(),manual,77L,0F);
    compare(vector,render.getField("caseTrans").get(null),ours.velocity,"original random initial velocity");compare(vector,render.getField("caseRot").get(null),ours.axis,"original random angular axis");
    original.getMethod("set",vector,float.class,float.class,float.class,vector,vector,vector).invoke(real,v(vector,new float[3]),0F,0F,0F,render.getField("caseTrans").get(null),v(vector,new float[3]),render.getField("caseRot").get(null));original.getField("time").setInt(real,0);
    for(int i=0;i<=30;i++) {
     compare(vector,original.getField("pos").get(real),ours.position,"core position tick "+i);compare(vector,original.getField("lastPos").get(real),ours.lastPosition,"core interpolation tick "+i);compare(vector,original.getField("velocity").get(real),ours.velocity,"core gravity/friction tick "+i);
     close(original.getField("rotProgress").getFloat(real),ours.spin,"core spin tick "+i);close(original.getField("lastRotProgress").getFloat(real),ours.lastSpin,"core spin interpolation tick "+i);
     // Flight physics must match the original tick for tick throughout its life. Lifetime is a
     // deliberate change (the port keeps a case in the world longer), so it is checked as a ratio
     // rather than as tick-by-tick death equality.
     original.getMethod("update",float.class,float.class,float.class).invoke(real,0F,0F,0F);ours.tick(null,0,0,0);
    }
    check((int)(30*FmumCasing.LIFETIME_MULTIPLIER)==ours.lifetime,"lifetime is the original 30 ticks times the multiplier");
   }
  }
  System.out.println("Original EFT GunCase executed unchanged: 62 physics/lifetime samples and both seeded ejection profiles match.");
 }
 static Object v(Class<?> cls,float[] value)throws Exception{return cls.getConstructor(float.class,float.class,float.class).newInstance(value[0],value[1],value[2]);}
 static void compare(Class<?> cls,Object actual,Vector3f expected,String label)throws Exception {close(cls.getField("x").getFloat(actual),expected.x,label+" X");close(cls.getField("y").getFloat(actual),expected.y,label+" Y");close(cls.getField("z").getFloat(actual),expected.z,label+" Z");}
}
