import com.flansmod.common.*;
import com.flansmod.common.GunActionState.*;
import com.flansmod.common.types.*;
import com.flansmod.client.render.*;
import com.flansmod.client.model.loader.*;
import org.joml.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Operation, constructor-data and matrix contracts. Never starts Minecraft or sends input. */
public final class VerifyGunActions {
    static void check(boolean value,String text) { if(!value) throw new AssertionError(text); }
    static void close(float a,float b,String text) { check(java.lang.Math.abs(a-b)<.00002F,text+": "+a+" vs "+b); }
    static void tick(GunActionState s,boolean wall) { s.tick(12,12,6,wall,false,false,.2F,.4F); }
    public static void main(String[] args) throws Exception {
        clocks();
        {
            var core=BytecodeModel.fromFile(Path.of("tools/offline/references/ModelGun_1.1.class"));
            close(FmumActionPose.scalar(core,"inertiaXConst",99),.1F,"actual core inertia X");
            close(FmumActionPose.scalar(core,"inertiaYMult",99),.8F,"actual core inertia damping");
            close(FmumActionPose.scalar(core,"checkAmmoTiltTimeRatio",99),1F/6,"actual core inspection tilt");
            check(Arrays.equals(core.fmumArrays.get("walkShakeAmplitudeY"),new float[]{.1F,.2F,-.8F}),"actual core array entries remain separate");
            check(Arrays.equals(core.fmumArrays.get("walkShakeRotMultZ"),new float[]{5,10,-40}),"actual core walking rotation array");
            System.out.println("Actual FMUM 1.1 ModelGun constructor scalar/array contracts passed.");
        }
        var coreGun=new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(Files.readAllBytes(Path.of("tools/offline/references/GunType_1.1.class"))).accept(coreGun,org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
        var stock=coreGun.methods.stream().filter(method->method.name.equals("getStockDistance")).findFirst().orElseThrow();
        var codes=new ArrayList<Integer>();for(var instruction:stock.instructions)if(instruction.getOpcode()>=0)codes.add(instruction.getOpcode());
        check(codes.equals(List.of(org.objectweb.asm.Opcodes.FCONST_0,org.objectweb.asm.Opcodes.FRETURN)),"actual EFT core stock distance is exactly zero");
        OfflinePack pack=new OfflinePack(Path.of(args[0])); pack.readTypes();
        int guns=0, ammo=0, chamber=0, bc=0, movingHands=0;
        for(var type:pack.types) if(type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG && !gun.magazineNames.isEmpty()) {
            guns++; if(gun.checkAmmoTime>0)ammo++; if(gun.checkChamberTime[0]>0)chamber++; if(gun.checkChamberTime[1]>0)bc++;
            var rig=ModelCache.rigFor(gun,gun.modelScale); check(rig!=null,"rig "+gun.shortName);
            var m=rig.animation;
            if(FmumActionPose.scalar(m,"leftHandAmmo",0)!=0 || FmumActionPose.scalar(m,"rightHandAmmo",0)!=0)movingHands++;
            for(Operation operation:new Operation[]{Operation.CHECK_AMMO,Operation.CHECK_CHAMBER,Operation.CHECK_CHAMBER_BC,Operation.REST,Operation.UNREST})
                for(float p:new float[]{0F,.1F,.3F,.7F,1F}) {
                    Frame f=new Frame(operation,p,.25F);
                    float ads=FmumActionPose.ads(m,f,1F);
                    Matrix4f pose=FmumFirstPersonPose.placement(new Matrix4f(),m,ads,.4F,0F,f,LegacyGunMotion.Frame.ZERO);
                    check(pose.isFinite(),gun.shortName+" finite body "+operation+" "+p);
                    check(FmumActionPose.arm(new Matrix4f(),m,true,f,gun.modelScale).isFinite(),gun.shortName+" finite left arm");
                    check(FmumActionPose.arm(new Matrix4f(),m,false,f,gun.modelScale).isFinite(),gun.shortName+" finite right arm");
                    check(FmumActionPose.magazine(new Matrix4f(),m,f).isFinite(),gun.shortName+" finite mag");
                    if(p==0 && operation!=Operation.REST && operation!=Operation.UNREST)close(ads,1,"inspection starts in original ADS pose");
                }
            // Identity action/motion must leave the already accepted static/ADS matrices unchanged.
            for(float ads:new float[]{0F,1F}) {
                var actual=FmumFirstPersonPose.placement(new Matrix4f(),m,ads,0,0,Frame.IDLE,LegacyGunMotion.Frame.ZERO);
                var expected=oldPlacement(m,ads);
                check(actual.equals(expected,.00002F),gun.shortName+" preserves accepted static/ADS");
            }
            if(gun.shortName.equals("AK74M")) {
                Frame draw=new Frame(Operation.NONE,0,1);
                var actual=FmumFirstPersonPose.placement(new Matrix4f(),m,0,0,0,draw,LegacyGunMotion.Frame.ZERO);
                var expected=base().translate(-.0625F-.1875F,-.0625F-.375F,.15625F-.1875F).rotateZ(FmumActionPose.rad(-60));
                check(actual.equals(expected,.00002F),"AK74M takeOut uses actual pack position and rotation");
                check(FmumActionPose.ads(m,new Frame(Operation.CHECK_AMMO,1,0),1)==0,"full inspection de-aims");
                var staticArm=FmumActionPose.arm(new Matrix4f(),m,true,Frame.IDLE,gun.modelScale);
                var checkArm=FmumActionPose.arm(new Matrix4f(),m,true,new Frame(Operation.CHECK_AMMO,.7F,0),gun.modelScale);
                check(!staticArm.equals(checkArm,.001F),"AK74M hand follows inspected magazine");
                System.out.println("AK74M action scalars="+m.fmumScalars+"; arrays="+m.fmumArrays.keySet());
            }
        }
        System.out.printf("EFT declarations: guns=%d ammo=%d chamber=%d BC=%d movingHands=%d%n",guns,ammo,chamber,bc,movingHands);
        check(guns==31 && ammo==31 && chamber==29 && bc==29,"all EFT operation declarations");
        check(movingHands>0,"pack arm-operation booleans extracted");
        motion();
        collisionRay();
        System.out.printf("PASS: FSM reversal/sounds/queuing, actual core scalar/array decoding, 31 takeOut/rest poses, %d ammo/%d chamber/%d BC declarations, %d moving inspection hands, 1550 finite operation frames, static/ADS preservation, inertia/acceleration/breath/walk and inverse rotation axes.%n",ammo,chamber,bc,movingHands);
    }
    static Matrix4f base() { return new Matrix4f().rotateZ(FmumActionPose.rad(20)).rotateY(FmumActionPose.rad(-5)).translate(.15F,.375F,-1.005F).rotateX(FmumActionPose.rad(.5F)).rotateZ(FmumActionPose.rad(5)).translate(0,.7F/160-.03F,-.35F/160); }
    static Matrix4f oldPlacement(ModelGeometry.Model m,float ads) {
        float[] t=FmumActionPose.vector(m,"holdingTranslate"),a=FmumActionPose.vector(m,"aimingTranslate"),r=FmumActionPose.vector(m,"holdingRotate"),b=FmumActionPose.vector(m,"aimingRotate");
        return FmumActionPose.rotate(base().translate(t[0]*(1-ads)+a[0]*ads,t[1]*(1-ads)+a[1]*ads,t[2]*(1-ads)+a[2]*ads),new float[]{r[0]*(1-ads)+b[0]*ads,r[1]*(1-ads)+b[1]*ads,r[2]*(1-ads)+b[2]*ads});
    }
    static void clocks() {
        GunActionState s=new GunActionState();s.equip(12);check(!s.ready(),"drawing blocks fire");
        for(int i=0;i<14;i++)tick(s,false);check(s.ready(),"draw ends (including the old float residual and last-frame gate)");
        s.request(Operation.CHECK_AMMO,60);int midpoint=0,out=0,back=0,ticks=0;
        do {tick(s,false);midpoint+=(s.events&4)!=0?1:0;out+=(s.events&1)!=0?1:0;back+=(s.events&2)!=0?1:0;ticks++;check(ticks<70,"inspection terminates");} while(!s.ready());
        check(midpoint==1 && out==1 && back==1,"inspection midpoint and both sounds exactly once");
        check(ticks>=60 && ticks<=63,"old strict >1/<0 timing");
        s.toggleRest();for(int i=0;i<12;i++)tick(s,false);close(s.progress,1,"manual rest 12 ticks");
        var stowed=new GunActionState();
        stowed.toggleRest();for(int i=0;i<12;i++)tick(stowed,false);close(stowed.progress,1,"stow fixture reaches rest");
        check(GunActions.dismissManualRestOnAim(stowed,true),"aim dismisses a manual stow (FMUM ItemGun:198-202)");
        check(!stowed.manualRest && stowed.operation==Operation.UNREST,"stow turned into an unrest, not a silent flag flip");
        stowed.toggleRest();for(int i=0;i<12;i++)tick(stowed,false);
        check(!GunActions.dismissManualRestOnAim(stowed,false),"a released aim key keeps the stow");
        check(stowed.manualRest && stowed.operation==Operation.REST,"stow survives an unpressed aim key");
        // Switching/folding an optic must not drop the player out of ADS: the fold animation runs
        // while the aim progress keeps following the input.
        var aimingState=new GunActionState();
        check(GunActions.aimingAllowed(aimingState,true),"aim rises while a sight fold is animating");
        aimingState.manualRest=true;check(!GunActions.aimingAllowed(aimingState,true),"manual stow still blocks the aim");
        aimingState.manualRest=false;aimingState.takeOut=1F;check(!GunActions.aimingAllowed(aimingState,true),"take-out motion still blocks the aim");
        aimingState.takeOut=0F;check(!GunActions.aimingAllowed(aimingState,false),"released aim key still lowers the aim");
        s.request(Operation.CHECK_CHAMBER,60);check(s.operation==Operation.UNREST,"unrest before queued inspection");
        for(int i=0;i<9;i++)tick(s,false);check(s.operation==Operation.CHECK_CHAMBER,"inspection starts after unrest");
        s.resume();for(int i=0;i<10;i++)tick(s,false);check(s.ready(),"fire/reload reverses inspection");
        s.reset();for(int i=0;i<6;i++)tick(s,true);close(s.progress,1,"wall rest uses unrestTime 6");
        check(!s.ready(),"wall blocks fire");for(int i=0;i<8;i++)tick(s,false);check(s.ready(),"leaving wall unrests");
		s.reset();tick(s,true);s.toggleRest();check(!s.manualRest && s.operation==Operation.UNREST,"manual key does not turn automatic wall rest into a sticky manual rest");s.reset();
        s.toggleRest();s.tick(12,12,6,false,true,false,0,0);check(s.operation==Operation.NONE,"sprint blocks entering rest");
    }
    static void motion() {
        var m=new ModelGeometry.Model();m.fmumScalars.put("breathShakeAmplitudeY",0F);m.fmumScalars.put("breathShakeAmplitudeZ",0F);m.fmumScalars.put("breathShakeRotMultY",0F);m.fmumScalars.put("breathShakeRotMultZ",0F);
        var motion=new LegacyGunMotion();motion.tick(m,10,-5,-1,0,0,false);var f=motion.frame(m,1,0,0);
        close(f.rotX(),.8F,"view yaw causes X inertia");close(f.rotYaw(),-.8F,"view yaw lags");close(f.rotPitch(),.4F,"pitch lags");close(f.transY(),-.12F,"vertical acceleration");
        motion.tick(m,0,0,0,0,0,false);var settled=motion.frame(m,1,0,0);close(settled.rotYaw(),-.64F,"inertia decays without input");
        var basis=new Matrix4f().rotateX(.3F).rotateY(.7F).rotateZ(.2F);
        var followed=FmumActionPose.follow(new Matrix4f(basis),basis,new LegacyGunMotion.Frame(0,0,0,12,0));
        var expected=new Matrix4f().rotateY(FmumActionPose.rad(12)).mul(basis);
        check(followed.equals(expected,.00002F),"follow axes are inverse local basis, not gun Y");
        close(LegacyGunMotion.weight(m,"missing",new float[]{1,2,3},1,0),2,"old ADS walk blend retains half holding term");
    }
    static void collisionRay() {
        var m=new ModelGeometry.Model();
        m.fmumPose.put("holdingTranslate",new float[]{-.0625F,-.0625F,.15625F});
        var gun=new GunType(new TypeFile(EnumType.gun,"ray_fixture",false));
        var local=FmumCollisionRay.local(m,gun,0,LegacyGunMotion.Frame.ZERO);
        close(local.origin().x,.446875F,"old barrel origin subtracts viewPoint X");
        close(local.origin().y,-.4625F,"old barrel origin subtracts viewPoint Y");
        close(local.origin().z,.15625F,"gun Z carried into wall ray");
        var world=FmumCollisionRay.world(local,0,0);
        close(world.direction().z,1,"yaw zero points south");
        close(world.origin().x,-.15625F,"gun-frame Z maps to world X");
        close(world.origin().z,.446875F,"gun-frame X maps to world forward");
        world=FmumCollisionRay.world(local,0,45);
        close(world.direction().y,-(float)java.lang.Math.sqrt(.5),"positive pitch points down");
        var type=new ModifiableType(new TypeFile(EnumType.attachment,"length_fixture",false));
        type.modelScale=.2F;type.bigHitBoxes.add(new float[][]{{4,0,0}});
        close(GunActions.gunLength(type,.5F,0),1.3F,"old hitBox defaults to scaled bigHitBox");
    }
}
