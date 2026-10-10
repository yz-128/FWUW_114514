import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Independent core/default-tree contracts; no client, player, world or generated input. */
public final class VerifyHoldingArms implements Opcodes {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void same(Matrix4f a,Matrix4f b,String s){check(a.equals(b,.00002F),s+"\n"+a+"\n"+b);}
 public static void main(String[] args)throws Exception {
  core(); inheritedArrays();
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
  int guns=0,attached=0,frames=0,pump2=0;
  for(var t:pack.types)if(t instanceof GunType g && g.itsType!=GunType.GunItsType.MAG && !g.magazineNames.isEmpty()) {
   guns++;var m=ModelCache.rigFor(g,g.modelScale).animation;
   for(boolean left:new boolean[]{true,false}) {
    var mount=FmumHoldingArmPose.forGun(g,left);
    var actual=FmumReloadPose.arm(new Matrix4f(),m,left,GunReloadState.Frame.IDLE,GunActionState.Frame.IDLE,g.modelScale,g);
    if(mount!=null){attached++;System.out.printf("%s %s hand: %s priority=%d mount=(%.6f %.6f %.6f), roll=%.1f%n",g.shortName,left?"left":"right",mount.type().shortName,mount.priority(),mount.x(),mount.y(),mount.z(),mount.roll());}
    else same(actual,FmumReloadPose.arm(new Matrix4f(),m,left,GunReloadState.Frame.IDLE,GunActionState.Frame.IDLE,g.modelScale),g.shortName+" unclaimed hand preserves model pose");
    for(var step:GunReloadState.Step.values())for(float p:new float[]{0,.2F,.5F,1}) {
     check(FmumReloadPose.arm(new Matrix4f(),m,left,new GunReloadState.Frame(step,p,p),GunActionState.Frame.IDLE,g.modelScale,g).isFinite(),g.shortName+" finite reload hand "+step);frames++;
    }
    // An operating hand wins over a grip. Returning to holding restores the grip.
    if(left && FmumActionPose.scalar(m,"leftHandAmmo",0)!=0) {
     var reload=new GunReloadState.Frame(GunReloadState.Step.UNLOAD_MAG,.5F,1);
     same(FmumReloadPose.arm(new Matrix4f(),m,true,reload,GunActionState.Frame.IDLE,g.modelScale,g),FmumReloadPose.arm(new Matrix4f(),m,true,reload,GunActionState.Frame.IDLE,g.modelScale),g.shortName+" unloading hand keeps magazine operation");
     var action=new GunActionState.Frame(GunActionState.Operation.CHECK_AMMO,.75F,0);
     same(FmumReloadPose.arm(new Matrix4f(),m,true,GunReloadState.Frame.IDLE,action,g.modelScale,g),FmumReloadPose.arm(new Matrix4f(),m,true,GunReloadState.Frame.IDLE,action,g.modelScale),g.shortName+" inspecting hand keeps magazine operation");
    }
   }
   if(List.of("Mosin","Model700","TOZ106").contains(g.shortName)) {
    check(m.parts.containsKey("pumpModel2") && m.parts.containsKey("pumpModel"),g.shortName+" separate bolt and handle");
    var raw=Path.of(args[0]).resolve(m.className.replace('.','/')+".class");
    var node=new ClassNode();new ClassReader(Files.readAllBytes(raw)).accept(node,0);
    var counts=new LinkedHashMap<String,Integer>();
    // Independent instruction audit counts the mesh calls under each typed array receiver.
    for(var method:node.methods) {
     String field=null;
     for(var insn:method.instructions) {
      if(insn instanceof FieldInsnNode f && f.getOpcode()==GETFIELD && f.desc.equals("[Lcom/flansmod/client/tmt/ModelRendererTurbo;"))field=f.name;
      if(insn instanceof MethodInsnNode call && List.of("addShapeBox","addBox","func_78790_a","addTrapezoid").contains(call.name))counts.merge(field,1,Integer::sum);
     }
    }
    for(var entry:counts.entrySet())check(m.parts.get(entry.getKey()).boxes.size()==entry.getValue(),g.shortName+" raw bytecode mesh count for "+entry.getKey()+": extracted "+m.parts.get(entry.getKey()).boxes.size()+", raw "+entry.getValue());
    System.out.println(g.shortName+" raw mesh counts="+counts);pump2++;
   }
  }
  GunType m4=pack.types.stream().filter(t->t instanceof GunType && "ColtM4A1".equals(t.shortName)).map(t->(GunType)t).findFirst().orElseThrow();
  var model=ModelCache.rigFor(m4,m4.modelScale).animation;
  var mount=FmumHoldingArmPose.forGun(m4,true);check(mount!=null && mount.type().shortName.equals("Length_Handguard_For_AR15"),"M4A1's default handguard wins");
  var upper=AttachmentType.getAttachment("M4A1_Upper_Receiver");
  var a=m4.slots[0];var b=upper.slots[1];
  var expected=new Matrix4f().translate(a.x,a.y,a.z).rotateX(FmumActionPose.rad(a.rotate))
    .translate(b.x,b.y,b.z).rotateX(FmumActionPose.rad(b.rotate))
    .translate(.1625F,-.1F,-.0875F).rotateX(FmumActionPose.rad(30)).rotateY(FmumActionPose.rad(-20)).rotateZ(FmumActionPose.rad(40)).scale(1.6F,.7F,.7F);
  same(FmumHoldingArmPose.arm(new Matrix4f(),m4,model,true),expected,"M4A1 raw pack handguard translation/XYZ rotation and gun arm scale, without attachment geometry scale");
  // No EFT class declares the optional TwoVector3f guide table populated by other packs.
  try(var files=Files.walk(Path.of(args[0]).resolve("com"))) {
   for(var p:files.filter(f->f.toString().endsWith(".class")).toList()) {
    var bytes=Files.readAllBytes(p);var strings=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);
    check(!strings.contains("TwoVector3f") && !strings.contains("leftArmPose") && !strings.contains("rightArmPose"),"EFT hand-guide maps remain empty: "+p);
   }
  }
  ties();check(guns==31 && pump2==3 && attached>0,"full EFT coverage");
  System.out.printf("PASS: actual core -128 priorities and parser keys, child-first/reverse-slot tie rules, M4A1 handguard matrix, %d/31 guns, %d attachment hands, %d finite reload hand frames, 3 independent pump/pump2 mesh audits, inherited-array pivot collision fixture, empty EFT guide tables.%n",guns,attached,frames);
 }
 static void core()throws Exception {
  try(var zip=new ZipFile("build/eft-core-reference.jar")) {
   var n=new ClassNode();try(var in=zip.getInputStream(zip.getEntry("com/flansmod/common/guns/AttachmentType.class"))){new ClassReader(in).accept(n,0);}
   for(String field:List.of("leftHandPriority","rightHandPriority")) {
    boolean found=false;for(var method:n.methods)if(method.name.equals("<init>"))for(var insn:method.instructions)
     if(insn instanceof FieldInsnNode f && f.name.equals(field) && f.getOpcode()==PUTFIELD && insn.getPrevious() instanceof IntInsnNode i && i.operand==-128)found=true;
    check(found,"core priority sentinel "+field);
   }
   var keys=new HashSet<String>();for(var method:n.methods)for(var insn:method.instructions)if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s)keys.add(s);
   check(keys.containsAll(List.of("LeftHandPose","RightHandPose")) && !keys.contains("LeftHandPriority") && !keys.contains("RightHandPriority"),"actual core only parses combined hand pose priority");
  }
 }
 static void inheritedArrays()throws Exception {
  var cw=new ClassWriter(0);cw.visit(V1_7,ACC_PUBLIC,"fixture/InheritedArms",null,"com/flansmod/client/model/ModelGun",null);
  var v=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);v.visitCode();
  for(int i=0;i<2;i++) {
   String part=i==0?"pumpModel":"pumpModel2";
   receiver(v,part);for(int j=0;j<3;j++)v.visitInsn(FCONST_0);for(int j=0;j<3;j++)v.visitInsn(ICONST_1);
   v.visitInsn(FCONST_0);v.visitMethodInsn(INVOKEVIRTUAL,"com/flansmod/client/tmt/ModelRendererTurbo","addBox","(FFFIIIF)V",false);
   receiver(v,part);v.visitLdcInsn((float)(i==0?1:7));v.visitLdcInsn((float)(i==0?2:8));v.visitLdcInsn((float)(i==0?3:9));
   v.visitMethodInsn(INVOKEVIRTUAL,"com/flansmod/client/tmt/ModelRendererTurbo","func_78793_a","(FFF)V",false);
  }
  v.visitInsn(RETURN);v.visitMaxs(8,1);v.visitEnd();cw.visitEnd();
  var m=BytecodeModel.fromStream(new ByteArrayInputStream(cw.toByteArray()));
  check(m.parts.size()==2 && m.parts.get("pumpModel").boxes.size()==1 && m.parts.get("pumpModel2").boxes.size()==1,"inherited numbered arrays remain distinct");
  check(m.parts.get("pumpModel").boxes.get(0).pivotX==1 && m.parts.get("pumpModel2").boxes.get(0).pivotX==7,"same renderer index in different arrays keeps independent pivots");
 }
 static void receiver(MethodVisitor v,String part){v.visitVarInsn(ALOAD,0);v.visitFieldInsn(GETFIELD,"fixture/InheritedArms",part,"[Lcom/flansmod/client/tmt/ModelRendererTurbo;");v.visitInsn(ICONST_0);v.visitInsn(AALOAD);}
 static void ties() {
  var g=new GunType(new TypeFile(EnumType.gun,"hand_fixture",false));g.slots=new ModifiableType.AttachableSlot[]{slot(.2F,.3F,.4F,90)};
  var parent=new AttachmentType(new TypeFile(EnumType.attachment,"hand_parent",false));parent.shortName="hand_parent";parent.leftHandPriority=15;parent.slots=new ModifiableType.AttachableSlot[]{slot(.1F,.2F,.3F,-45)};
  var child=new AttachmentType(new TypeFile(EnumType.attachment,"hand_child",false));child.shortName="hand_child";child.leftHandPriority=15;
  var n=new ModifiableType.DefaultAttachment();n.name=parent.shortName;n.slot=0;
  var c=new ModifiableType.DefaultAttachment();c.name=child.shortName;c.slot=0;n.children.add(c);g.defaultAttachments.add(List.of(n));
  var winner=FmumHoldingArmPose.forGun(g,true);check(winner.type()==child,"equal-priority child wins over parent");
  var expected=new Matrix4f().translate(.2F,.3F,.4F).rotateX(FmumActionPose.rad(90)).translate(.1F,.2F,.3F).rotateX(FmumActionPose.rad(-45));
  same(new Matrix4f().translate(winner.x(),winner.y(),winner.z()).rotateX(FmumActionPose.rad(winner.roll())),expected,"nested canted mount uses parent-local Y/Z and accumulated roll");
  child.leftHandPriority=14;check(FmumHoldingArmPose.forGun(g,true).type()==parent,"higher priority wins");
  check(FmumHoldingArmPose.forGun(g,false)==null,"unclaimed right hand stays at gun");
 }
 static ModifiableType.AttachableSlot slot(float x,float y,float z,float r){var s=new ModifiableType.AttachableSlot();s.x=x;s.y=y;s.z=z;s.rotate=r;return s;}
}
