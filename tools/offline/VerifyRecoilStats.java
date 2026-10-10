import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.client.GunDetails;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.util.*;

/** Actual EFT definitions, actual item data, and independent FMUM equation/matrix checks. */
public final class VerifyRecoilStats {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    static void close(float a,float b,String text){check(Math.abs(a-b)<.00002F,text+": "+a+" vs "+b);}
    static GunType gun(OfflinePack p,String name){return (GunType)p.types.stream().filter(t->name.equals(t.shortName)).findFirst().orElseThrow();}
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        ((net.minecraft.core.MappedRegistry<?>)net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
        core();parserFixtures();cadence();camera();
        var ak=gun(pack,"AK74M");var glock=gun(pack,"Glock17");
        var v=GunStats.effective(ak);
        // The pack's only recoil-reducing AK74M defaults are its stock and muzzle brake.
        close(v.pitch(),.77F*.9F,"AK74M default stock/muzzle pitch product");
        close(v.randomYaw(),.77F*.9F,"both attachment RecoilMultiplier operands applied");
        close(v.randomPitch(),.2F*.77F*.9F,"pitch random range scaled with pitch");
        close(v.rpm(),650F,"configured AK74M RPM");
        close(ak.antiModelRecoil[0],.7F,"pack misspelling is ignored by core");
        close(glock.antiModelRecoil[0],.5F,"proper Glock key affects decay");
        close(gun(pack,"Mosin").modelRecoil[0],.45F,"ModelRecoilConst is inactive in supplied core");
        close(gun(pack,"ADAR").modelRecoil[2],1.8F,"omitted model constants inherit core defaults");
        var mag=gun(pack,"6L20_545x39_30Rounds");
        Item weapon=VerifyMagazineData.register(ak,"recoil_gun"),magItem=VerifyMagazineData.register(mag,"recoil_mag");
        var stack=new ItemStack(weapon);GunMagazine.set(stack,ak,0,new ItemStack(magItem));
        var before=stack.get(DataComponents.CUSTOM_DATA).copyTag();
        var sample=GunStats.shot(ak,stack,false,.5F,.75F);
        close(sample.pitch(),v.pitch(),"default-mag multiplier cannot zero recoil");
        close(sample.yaw(),v.randomYaw()*.25F,"horizontal random sample");
        close(GunStats.shot(ak,stack,true,.5F,.75F).pitch(),sample.pitch()*.8F,"core crouch reduction");
        var expanded=new ArrayList<Component>();GunDetails.append(expanded,ak,stack,true);
        check(expanded.size()>10,"Shift-expanded firearm numbers");
        var collapsed=new ArrayList<Component>();GunDetails.append(collapsed,ak,stack,false);
        check(collapsed.size()<expanded.size(),"Shift collapses detailed numbers");
        check(stack.get(DataComponents.CUSTOM_DATA).copyTag().equals(before),"stats/recoil inspection never changes item data");
        check(ak.modes.contains(GunType.EnumFireMode.SAFE),"EFT safe mode remains safe rather than semi auto");
        ak.setModeFor(stack,GunType.EnumFireMode.SAFE);check(ak.modeFor(stack)==GunType.EnumFireMode.SAFE,"safe mode NBT round trip");
        var bt=(BulletType)pack.types.stream().filter(t->"5.45x39mmBT".equals(t.shortName)).findFirst().orElseThrow();
        close(GunStats.firingSpread(v,bt,false),2F*.9F/2F,"actual tracer hip spread");
        close(GunStats.firingSpread(v,bt,true),1.25F*.9F/2F,"actual tracer ADS spread");
        var buck=(BulletType)pack.types.stream().filter(t->"12G7mm8.5Magnum".equals(t.shortName)).findFirst().orElseThrow();
        var toz=GunStats.effective(gun(pack,"TOZ106"));
        close(GunStats.firingSpread(toz,buck,false),5F/2F,"pellet hip multiplier is second operand");
        close(GunStats.firingSpread(toz,buck,true),10F/2F,"pellet ADS multiplier is first operand");
        check(buck.numBullets==9,"9-projectile ammunition is retained");
        int count=0,frames=0;StringBuilder table=new StringBuilder("# EFT 枪械数值清单\n\n含默认附件；后坐力为站姿、弹匣倍率前；散布为原包枪械参数，发射时还应用子弹倍率及 1/2。\n\n|枪械|RPM|裸枪垂直|装配垂直|水平随机全范围|腰射 / ADS|弹速系数|完整换弹秒|\n|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for(var type:pack.types)if(type instanceof GunType g && g.itsType!=GunType.GunItsType.MAG && !g.magazineNames.isEmpty()) {
            count++;var model=ModelCache.load(g);var stats=GunStats.effective(g);
            check(model!=null && stats.delay()>0 && Float.isFinite(stats.pitch()),"finite stats "+g.shortName);
            table.append(String.format(Locale.ROOT,"|%s|%.0f|%.3f|%.3f|%.3f|%.3f / %.3f|%.3f|%.2f|%n",g.shortName,stats.rpm(),g.recoilPitch,stats.pitch(),stats.randomYaw(),stats.hipSpread(),stats.adsSpread(),stats.speed(),g.effectiveReloadTime()/20F));
            var recoil=new FmumRecoil();
            for(int tick=0;tick<80;tick++) {
                if(tick<30 && tick%2==0) recoil.shoot(new GunStats.Shot(g,stats,stats.pitch(),stats.randomYaw()*.25F,false),model);
                recoil.tick();
                for(float ads:new float[]{0F,1F}) for(float reload:new float[]{0F,.5F,1F}) {
                    var frame=recoil.frame(.5F,new Random(tick));
                    var pose=FmumFirstPersonPose.placement(new Matrix4f(),model,ads,0,reload,GunActionState.Frame.IDLE,LegacyGunMotion.Frame.ZERO,frame);
                    FmumFirstPersonPose.aim(pose,g,ads);check(pose.isFinite(),"finite burst/ADS/reload pose "+g.shortName);frames++;
                }
            }
            var settled=recoil.frame(1F,null);
            check(Math.abs(settled.rz())<.01F && Math.abs(settled.tx())<.01F,"post-burst recovery "+g.shortName);
        }
        check(count==31,"all 31 EFT firearms");
        Files.writeString(Path.of("reports/eft/枪械数值清单.md"),table.toString());
        equations(ak,ModelCache.load(ak));
        System.out.println("PASS: original-core defaults and exact keys; attachment pitch/yaw; native ItemStack read-only stats and magazine recoil; safe-mode persistence; mixed-round/pellet spread; 650/950/1100 RPM; camera recovery; independent six-axis/matrix/slide equations; "+count+" firearms, "+frames+" burst/ADS/reload frames.");
        System.out.println("AK74M base="+GunStats.base(ak)+" effective="+v);
    }
    static void parserFixtures() {
        TypeFile tf=new TypeFile(EnumType.bullet,"single_spread",false);tf.lines.add("SpreadMultiplier 2");
        var b=new BulletType(tf);b.read(tf);close(b.aimingBulletSpreadMultiplier,2F,"single ammo spread operand is ADS");close(b.bulletSpreadMultiplier,1F,"single ammo spread leaves hip default");
        TypeFile af=new TypeFile(EnumType.attachment,"axis_fixture",false);
        af.lines.add("RecoilMultiplier .8 .6");af.lines.add("ModelRecoilMultiplier 2 3 4 5 6");
        var attachment=new AttachmentType(af);attachment.read(af);
        close(attachment.recoilMultiplier,.8F,"independent attachment pitch operand");close(attachment.recoilYawMultiplier,.6F,"independent attachment yaw operand");
        check(Arrays.equals(attachment.modelRecoilMultipliers,new float[]{2,3,4,5,6}),"five independently indexed model-recoil attachment operands");
    }
    static void core()throws Exception {
        var actual=BytecodeModel.fromFile(Path.of("tools/offline/references/GunType_1.1.class"));
        close(actual.fmumScalars.get("recoilPitch"),1F,"core pitch default");
        close(actual.fmumScalars.get("rndRecoilYawRange"),1F,"core yaw random default");
        close(actual.fmumScalars.get("recoilRotZConst"),.45F,"core model Z default");
        close(actual.fmumScalars.get("antiRecoilRotZConst"),.7F,"core retention default");
        var m=BytecodeModel.fromFile(Path.of("tools/offline/references/ModelGun_1.1.class"));
        close(m.fmumScalars.get("recoilShakeRotX"),2F,"core inherited shake");
        close(m.fmumScalars.get("antiRecoilShakeConst"),.36F,"core shake retention");
    }
    static void cadence() {
        for(float rpm:new float[]{650,950,1100}) {
            float cooldown=0;int shots=0;
            for(int tick=0;tick<1200;tick++) {
                if(cooldown>0) cooldown-=1F;
                if(cooldown<=0) {cooldown=GunFiring.nextShotDelay(cooldown,1200F/rpm);shots++;}
            }
            check(Math.abs(shots-rpm)<=1F,"fractional RPM "+rpm+" actual "+shots);
        }
    }
    static void camera() {
        var state=new FmumRecoil.Camera();state.shoot(1F,.25F);
        var d=state.tick();close(d.pitch(),-.64F,"core first tick view pitch");close(d.yaw(),-.2F,"core first tick view yaw");
        float pitch=d.pitch(),yaw=d.yaw();
        for(int i=0;i<180;i++) {d=state.tick();pitch+=d.pitch();yaw+=d.yaw();}
        close(pitch,0F,"pitch returns to original aim");close(yaw,-1F,"core yaw accumulates a settled angular offset");
        state.reset();d=state.tick();close(d.pitch(),0F,"world reset pitch");close(d.yaw(),0F,"world reset yaw");
    }
    static void equations(GunType g,ModelGeometry.Model m) {
        var stats=GunStats.effective(g);var shot=new GunStats.Shot(g,stats,1F,.25F,false);var state=new FmumRecoil();
        state.shoot(shot,m);state.tick();var f=state.frame(0F,null);
        close(f.rx(),.5F,"original X roll impulse");close(f.ry(),.5F,"original Y yaw impulse");close(f.rz(),3.5F,"original Z pitch impulse");
        close(f.tx(),-.0035F,"pitch and absolute-yaw X translation");close(f.ty(),.006F,"pitch Y translation");close(f.tz(),.00025F,"signed yaw Z translation");
        close(state.fov(0F),.0175F,"original model-dependent FOV impulse");
        close(state.frame(1F,null).rz(),2.45F,"per-tick .7 retention");
        state.shoot(shot,m);state.tick();close(state.frame(0F,null).rz(),5.95F,"sustained fire accumulates retained previous impulse");
        var pose=FmumFirstPersonPose.placement(new Matrix4f(),m,0,0,1,GunActionState.Frame.IDLE,LegacyGunMotion.Frame.ZERO,f);
        var expected=new Matrix4f().rotateZ(rad(20)).rotateY(rad(-5)).translate(.15F,.375F,-1.005F)
            .rotateX(rad(.5F)).rotateZ(rad(5)).translate(0,.7F/160-.03F,-.35F/160)
            .translate(-.0625F-.125F-.0035F,-.0625F-.03125F+.006F,.15625F-.125F+.00025F)
            .rotateX(rad(15.5F)).rotateY(rad(5.5F)).rotateZ(rad(13.5F));
        check(pose.equals(expected,.00002F),"core translation precedes holding rotation, then reload+recoil XYZ");
        var animation=new GunAnimations();animation.doShoot(shot,m,0,1);animation.update();close(animation.gunSlide,0F,"AK slide acceleration .5 + .5*slide closes in one tick");
        animation.update();close(animation.lastGunSlide,0F,"slide returns to exact zero");
    }
    static float rad(float value){return (float)Math.toRadians(value);}
}
