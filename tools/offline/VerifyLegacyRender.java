import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.FmumFirstPersonPose;
import com.flansmod.client.render.LegacyThirdPersonPose;
import com.flansmod.client.render.LegacySightPose;
import com.flansmod.client.LegacyGunArmPose;
import com.flansmod.common.types.*;
import org.objectweb.asm.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;

/** Contract regressions for independent per-renderer UVs and the real EFTArm SRG fields. */
public final class VerifyLegacyRender implements Opcodes {
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    static void close(float actual,float expected,String message) { check(Math.abs(actual-expected)<.00001F,message+": "+actual+" != "+expected); }
    public static void main(String[] args)throws Exception {
        sightFixtures();
        // Start at the modern held-item layer and compare to the actual 1.7.10 outer chain.
        // A nonzero offset catches accidentally applying the model offset twice.
        Matrix4f modern=new Matrix4f().rotateX((float)Math.toRadians(-90)).rotateY((float)Math.toRadians(180))
            .translate(1F/16,.125F,-.625F);
        LegacyThirdPersonPose.handFrame(modern,false,false);
        LegacyThirdPersonPose.placement(modern,.2F,-.15F,.01F);
        Matrix4f legacy=new Matrix4f().translate(-1F/16,7F/16,1F/16).translate(0,.1875F,0)
            .scale(.625F,-.625F,.625F).rotateX((float)Math.toRadians(-100)).rotateY((float)Math.toRadians(45))
            .translate(0,-.3F,0).scale(1.5F).rotateY((float)Math.toRadians(50)).rotateZ((float)Math.toRadians(335))
            .translate(-.9375F,-.0625F,0).rotateZ((float)Math.toRadians(35)).rotateY((float)Math.toRadians(-5))
            .translate(.75F,-.22F,-.08F).scale(1,1,-1).translate(.2F,-.15F,.01F);
        float[] actual=modern.get(new float[16]),expected=legacy.get(new float[16]);
        for(int i=0;i<16;i++)close(actual[i],expected[i],"third-person vanilla/Forge/FMUM matrix component "+i);
        check(modern.determinant()>0,"right-hand third-person mirrors cancel");
        ModelGeometry.Model fixture=BytecodeModel.fromStream(new ByteArrayInputStream(fixture()));
        BakedRig baked=BakedRig.bake(fixture,1F);
        check(baked.quads.size()==12,"two separate boxes must bake twelve faces");
        float u0=Arrays.stream(baked.quads.get(0).uvs()).map(v->v[0]).min(Float::compare).get();
        float u1=Arrays.stream(baked.quads.get(6).uvs()).map(v->v[0]).min(Float::compare).get();
        close(u0,8F/64,"first box keeps first renderer's texture origin");
        close(u1,24F/64,"second box keeps second renderer's texture origin");
        close(u1-u0,.25F,"renderer texture origins remain independent");
        OfflinePack pack=new OfflinePack(Paths.get(args[0]));pack.readTypes();
        var arm=ModelCache.load("EFTRDef.EFTArm");
        var angles=arm.rendererRotations.get("defaultModel#0");
        close(angles[0],-90,"EFTArm SRG rotateAngleX");close(angles[1],90,"EFTArm SRG rotateAngleY");
        var armRig=BakedRig.bake(arm,1F);
        close(armRig.minX,-11F/16,"arm shoulder endpoint");close(armRig.maxX,1F/16,"arm hand endpoint");
        var uv=ModelCache.uvScaleForDimensions(arm,64,64);close(uv[0],1,"skin U untouched");close(uv[1],.5F,"skin V scales to modern 64x64");
        check(ModelCache.load("EFTRWea.Model700")!=null,"Model700 double-prefix fallback");
        check(ModelCache.load("EFTRWea.Model700Stock")!=null,"Model700Stock double-prefix fallback");
        int firearms=0;
        for(InfoType t:pack.types)if(t instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG && !gun.magazineNames.isEmpty()) {
            var model=ModelCache.load(gun);check(FmumFirstPersonPose.hasPose(model),"missing FMUM pose: "+gun.shortName);firearms++;
            check(LegacyGunArmPose.forType(gun,false)==net.minecraft.client.model.HumanoidModel.ArmPose.BOW_AND_ARROW,
                "EFT third-person raised arms: "+gun.shortName);
            check(LegacyGunArmPose.forType(gun,true)==net.minecraft.client.model.HumanoidModel.ArmPose.ITEM,
                "sprinting lowers arms: "+gun.shortName);
            Matrix4f m=FmumFirstPersonPose.itemFrame(new Matrix4f().translate(.56F,-.52F,-.72F));
            FmumFirstPersonPose.placement(m,model,1,0,0);FmumFirstPersonPose.aim(m,gun,1);
            var selected=LegacySightPose.forGun(gun);
            Vector3f sight=m.transformPosition(new Vector3f(0,selected.y(),selected.z()));
            check(Float.isFinite(sight.x)&&Float.isFinite(sight.y)&&sight.z<0,"finite ADS sight: "+gun.shortName);
            if(gun.shortName.equals("AK74M")) {
                check(selected.source().equals("6P1Sb2AK74Rearsight"),"AK74M uses its rear sight, not the gun body center");
                close(selected.y(),.4F,"AK74M rear sight: slot 31/16*.2 + sight 1/16*.2");
                close(selected.z(),0,"AK74M rear sight Z");close(selected.roll(),0,"AK74M rear sight roll");
                close(gun.aimCenterY,.4375F,"body center remains unchanged");
                check(Math.abs(sight.x)<.0001F&&Math.abs(sight.y)<.0001F,"AK74M sight line intercept projects to center");
                Vector3f rear=m.transformPosition(new Vector3f(selected.x(),selected.y(),selected.z()));
                float ndcX=rear.x/(-rear.z*.700208F*854/480),ndcY=rear.y/(-rear.z*.700208F);
                check(Math.abs(ndcX)*854/2<1&&Math.abs(ndcY)*480/2<1,"AK74M rear sight mount projects within one pixel of center");
                System.out.printf("AK74M selected %s; ADS rear sight eye=(%.6f,%.6f,%.6f), NDC=(%.6f,%.6f)%n",selected,rear.x,rear.y,rear.z,ndcX,ndcY);
            }
        }
        check(firearms==31,"all 31 EFT firearms checked (got "+firearms+")");
        System.out.println("PASS: default sight selection and nested canted mounts, AK74M rear sight centering, full third-person 1.7.10 frame, 31/31 raised gun poses, per-renderer UV fixture, EFTArm SRG rotations and bounds, 64x64 skin UV, Model700 lookup, 31/31 FMUM firearms.");
    }
    static void sightFixtures() {
        GunType gun=new GunType(new TypeFile(EnumType.gun,"fixture_gun",false));
        gun.shortName="fixture_gun";gun.aimCenterY=.7F;gun.aimCenterZ=.2F;
        close(LegacySightPose.forGun(gun).y(),.7F,"no-sight gun retains body center");
        AttachmentType mount=new AttachmentType(new TypeFile(EnumType.attachment,"fixture_mount",false));
        mount.shortName="fixture_mount";
        AttachmentType sight=new AttachmentType(new TypeFile(EnumType.attachment,"fixture_sight",false));
        sight.shortName="fixture_sight";sight.itsType="sight";sight.aimCenterY=.1F;sight.aimCenterZ=.03F;
        var slot=new ModifiableType.AttachableSlot();slot.x=.2F;slot.y=.1F;slot.z=.3F;slot.rotate=90;
        gun.slots=new ModifiableType.AttachableSlot[]{slot};
        var childSlot=new ModifiableType.AttachableSlot();childSlot.x=.4F;childSlot.y=.2F;childSlot.z=.05F;childSlot.rotate=-45;
        mount.slots=new ModifiableType.AttachableSlot[]{childSlot};
        var parent=new ModifiableType.DefaultAttachment();parent.name=mount.shortName;parent.slot=0;
        var child=new ModifiableType.DefaultAttachment();child.name=sight.shortName;child.slot=0;
        parent.children.add(child);gun.defaultAttachments.add(List.of(parent));
        var result=LegacySightPose.forGun(gun);
        close(result.roll(),45,"nested canted sight accumulates mount rotations");
        close(result.x(),.6F,"nested sight X");
        close(result.y(),.099497475F,"nested sight Y rotates slot at parent angle and aim offset at final angle");
        close(result.z(),.591923882F,"nested sight Z rotates slot and aim offset");

        // Original group 0 comes from reverse slot order, even if another line is farther
        // forward. Only sights on that group's Y/Z line are then ordered by descending X.
        var lowSlot=new ModifiableType.AttachableSlot();lowSlot.x=20;lowSlot.y=.1F;
        var nearSlot=new ModifiableType.AttachableSlot();nearSlot.x=1;nearSlot.y=.4F;
        var farSlot=new ModifiableType.AttachableSlot();farSlot.x=3;farSlot.y=.4F;
        gun.slots=new ModifiableType.AttachableSlot[]{lowSlot,nearSlot,farSlot};
        gun.defaultAttachments.clear();
        sight.aimCenterZ=0;
        for(int i=0;i<3;i++) {
            var node=new ModifiableType.DefaultAttachment();node.name=sight.shortName;node.slot=i;
            gun.defaultAttachments.add(List.of(node));
        }
        result=LegacySightPose.forGun(gun);
        close(result.x(),3,"first sight group sorts along X without selecting another Y/Z line");
        close(result.y(),.5F,"reverse slot traversal chooses the original first sight group");
        AttachmentType.attachments.remove(mount);AttachmentType.attachments.remove(sight);
    }
    static byte[] fixture() {
        String cn="com/flansmod/client/model/ModelUVFixture",turbo="com/flansmod/client/tmt/ModelRendererTurbo";
        ClassWriter cw=new ClassWriter(0);cw.visit(V1_7,ACC_PUBLIC,cn,null,"java/lang/Object",null);
        cw.visitField(ACC_PUBLIC,"gunModel","[L"+turbo+";",null,null).visitEnd();
        var m=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();
        m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        m.visitVarInsn(ALOAD,0);m.visitInsn(ICONST_2);m.visitTypeInsn(ANEWARRAY,turbo);m.visitFieldInsn(PUTFIELD,cn,"gunModel","[L"+turbo+";");
        for(int i=0;i<2;i++) {
            m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,cn,"gunModel","[L"+turbo+";");m.visitInsn(ICONST_0+i);
            m.visitTypeInsn(NEW,turbo);m.visitInsn(DUP);m.visitVarInsn(ALOAD,0);
            m.visitIntInsn(SIPUSH,16*i);m.visitInsn(ICONST_0);m.visitIntInsn(SIPUSH,64);m.visitIntInsn(SIPUSH,32);
            m.visitMethodInsn(INVOKESPECIAL,turbo,"<init>","(Lnet/minecraft/client/model/ModelBase;IIII)V",false);m.visitInsn(AASTORE);
            m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,cn,"gunModel","[L"+turbo+";");m.visitInsn(ICONST_0+i);m.visitInsn(AALOAD);
            m.visitInsn(FCONST_0);m.visitInsn(FCONST_0);m.visitInsn(FCONST_0);m.visitInsn(ICONST_4);m.visitIntInsn(BIPUSH,12);m.visitInsn(ICONST_4);m.visitInsn(FCONST_0);
            m.visitMethodInsn(INVOKEVIRTUAL,turbo,"addBox","(FFFIIIF)V",false);
        }
        m.visitInsn(RETURN);m.visitMaxs(16,1);m.visitEnd();cw.visitEnd();return cw.toByteArray();
    }
}
