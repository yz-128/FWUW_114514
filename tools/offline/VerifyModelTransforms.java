import com.flansmod.client.model.loader.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Constructor order checked against independent vertex/pivot mutations and original pack bytecode. */
public final class VerifyModelTransforms implements Opcodes {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<2E-6F,message+" actual="+actual+" expected="+expected);}
    static ModelGeometry.Model read(byte[] bytes)throws Exception{return BytecodeModel.fromStream(new ByteArrayInputStream(bytes));}
    static byte[] fixture(String operations){
        ClassWriter writer=new ClassWriter(0);writer.visit(V1_7,ACC_PUBLIC,"fixture/ModelOrdered",null,"com/flansmod/client/model/ModelGun",null);
        var m=writer.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();
        m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,"com/flansmod/client/model/ModelGun","<init>","()V",false);
        m.visitVarInsn(ALOAD,0);m.visitInsn(ICONST_1);m.visitTypeInsn(ANEWARRAY,"com/flansmod/client/tmt/ModelRendererTurbo");m.visitFieldInsn(PUTFIELD,"fixture/ModelOrdered","gunModel","[Lcom/flansmod/client/tmt/ModelRendererTurbo;");
        m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,"fixture/ModelOrdered","gunModel","[Lcom/flansmod/client/tmt/ModelRendererTurbo;");m.visitInsn(ICONST_0);
        m.visitTypeInsn(NEW,"com/flansmod/client/tmt/ModelRendererTurbo");m.visitInsn(DUP);m.visitVarInsn(ALOAD,0);
        for(int v:new int[]{0,0,64,64})m.visitLdcInsn(v);
        m.visitMethodInsn(INVOKESPECIAL,"com/flansmod/client/tmt/ModelRendererTurbo","<init>","(Lnet/minecraft/client/model/ModelBase;IIII)V",false);m.visitInsn(AASTORE);
        renderer(m);for(float v:new float[]{1,2,3})m.visitLdcInsn(v);for(int v:new int[]{2,3,4})m.visitLdcInsn(v);m.visitLdcInsn(0F);
        m.visitMethodInsn(INVOKEVIRTUAL,"com/flansmod/client/tmt/ModelRendererTurbo","addBox","(FFFIIIF)V",false);
        renderer(m);for(float v:new float[]{4,5,6})m.visitLdcInsn(v);m.visitMethodInsn(INVOKEVIRTUAL,"com/flansmod/client/tmt/ModelRendererTurbo","setRotationPoint","(FFF)V",false);
        for(char op:operations.toCharArray()){
            m.visitVarInsn(ALOAD,0);
            if(op=='F')m.visitMethodInsn(INVOKEVIRTUAL,"fixture/ModelOrdered","flipAll","()V",false);
            else {for(float v:new float[]{.5F,1.25F,-2.5F})m.visitLdcInsn(v);m.visitMethodInsn(INVOKEVIRTUAL,"fixture/ModelOrdered","translateAll","(FFF)V",false);}
        }
        m.visitInsn(RETURN);m.visitMaxs(12,1);m.visitEnd();writer.visitEnd();return writer.toByteArray();
    }
    static void renderer(MethodVisitor m){m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,"fixture/ModelOrdered","gunModel","[Lcom/flansmod/client/tmt/ModelRendererTurbo;");m.visitInsn(ICONST_0);m.visitInsn(AALOAD);}
    static void fixtures()throws Exception{
        for(String ops:List.of("","T","F","TF","FT","FF","TFF","FTF","TFT","TFFT","FTFTF")){
            var model=read(fixture(ops));var rig=BakedRig.bake(model,1);float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
            for(int corner=0;corner<8;corner++){
                float[] p={4,5,6},v={1+((corner&1)!=0?2:0),2+((corner&2)!=0?3:0),3+((corner&4)!=0?4:0)};
                for(char op:ops.toCharArray())if(op=='F'){p[1]=-p[1];p[2]=-p[2];v[1]=-v[1];v[2]=-v[2];}else{p[0]+=.5F;p[1]+=1.25F;p[2]-=2.5F;}
                for(int i=0;i<3;i++){float value=(p[i]+v[i])/16;min[i]=Math.min(min[i],value);max[i]=Math.max(max[i],value);}
            }
            float[] actual={rig.minX,rig.minY,rig.minZ,rig.maxX,rig.maxY,rig.maxZ};
            for(int i=0;i<3;i++){near(actual[i],min[i],ops+" minimum "+i);near(actual[i+3],max[i],ops+" maximum "+i);}
            check(rig.quads.size()==6,"Fixture topology retained "+ops);
        }
    }
    static void auditCallSites(Path pack)throws Exception {
        int models=0,transforms=0;
        try(var paths=Files.walk(pack)){
            for(Path path:paths.filter(p->p.toString().endsWith(".class")&&p.toString().replace('\\','/').contains("com/flansmod/client/model/")).toList()){
                ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(path)).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);models++;
                for(var method:node.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&List.of("translateAll","flipAll").contains(call.name)){
                    check(method.name.equals("<init>"),"Model transforms outside constructor require a call-order audit: "+path+" "+method.name);transforms++;
                }
            }
        }
        System.out.printf("PASS source transform call sites: models=%d constructor_transforms=%d; no helper/override double counting.%n",models,transforms);
    }
    public static void main(String[] args)throws Exception{
        fixtures();auditCallSites(Path.of(args[0]));Path file=Path.of(args[0]).resolve("com/flansmod/client/model/EFTRWea/ModelBocchiTheGlock18C.class");
        byte[] original=Files.readAllBytes(file);ClassNode cn=new ClassNode();new ClassReader(original).accept(cn,0);
        List<String> calls=new ArrayList<>();
        for(var method:cn.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&List.of("translateAll","flipAll").contains(call.name)){
            calls.add(call.name);call.name="offlineIgnoredModelTransform";
        }
        check(calls.equals(List.of("translateAll","flipAll")),"Actual Bocchi constructor translates then flips");
        var writer=new ClassWriter(0);cn.accept(writer);var raw=read(writer.toByteArray());var actual=read(original);
        near(actual.translateAllY,-.5F,"Bocchi half-pixel translation is flipped");check(actual.flipped,"Original gun geometry still flipped");
        int boxes=0;
        for(var entry:raw.pivots.entrySet()){
            float[] before=entry.getValue(),after=actual.pivots.get(entry.getKey());
            near(after[0],before[0],entry.getKey()+" original X");near(after[1],-(before[1]+.5F),entry.getKey()+" original Y order");near(after[2],-before[2],entry.getKey()+" original Z");boxes++;
        }
        check(boxes==329,"All original pistol renderer pivots checked");
        var old=BakedRig.bake(raw,.2F);var fixed=BakedRig.bake(actual,.2F);check(old.quads.size()==fixed.quads.size(),"Pistol topology unchanged");
        var slide=BytecodeModel.fromFile(Path.of(args[0]).resolve("com/flansmod/client/model/EFTRAtt/ModelGlock18CSlideBocchi.class"));
        near(slide.translateAllY,0,"Slide has no translation to change");check(slide.flipped,"Slide's original flip retained");
        System.out.printf("PASS model constructor transforms: fixtures=11 Bocchi_pivots=%d assertions=%d; original translation/flip order, double flips, topology and unshifted slide.%n",boxes,checks);
    }
}
