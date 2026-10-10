import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.types.*;
import java.nio.file.Path;
import java.util.*;
import org.joml.Matrix4f;

/** Original pack bullet seats, reload phase timing, hand and magazine matrices; no game/input. */
public final class VerifyReloadRendering {
	static void check(boolean value,String text) {if(!value)throw new AssertionError(text);}
	static void close(float a,float b,String text) {check(java.lang.Math.abs(a-b)<.00002F,text+": "+a+" vs "+b);}
	public static void main(String[] args)throws Exception {
		var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();int mags=0,seated=0,guns=0,frames=0;
		for(var type:pack.types) if(type instanceof GunType gun && GunReloading.supports(gun)) {
			var rig=ModelCache.rigFor(gun,gun.modelScale);check(rig!=null,"pack rig "+gun.shortName);
			if(gun.itsType==GunType.GunItsType.MAG) {
				mags++;if(!FmumBulletPose.seats(rig.animation,gun.ammoCapacity).isEmpty())seated++;
				else System.out.println("Original pack has no bullet seating arrays: "+gun.shortName+" model="+gun.modelString);
				check(FmumBulletPose.seats(rig.animation,0).isEmpty(),"empty magazine shows no bullets");
				for(int count=1;count<=gun.ammoCapacity;count++)
					for(var seat:FmumBulletPose.seats(rig.animation,count)) {
						check(seat.ammunitionIndex()>=0 && seat.ammunitionIndex()<count,"seats only actual ammunition");
						check(seat.pose().isFinite(),gun.shortName+" finite seated bullet");
					}
				if(gun.shortName.equals("6L20_545x39_30Rounds")) {
					var vectors=rig.animation.fmumVectorArrays;
					check(vectors.get("bulletAttachPoint1").length==3 && vectors.get("bulletAttachPoint2").length==3,"actual 6L20 vector arrays");
					close(vectors.get("bulletAttachPoint1")[1][2],-.00625F,"original odd/even negative stagger");
					close(vectors.get("bulletAttachPoint2")[1][2],.00625F,"original odd/even positive stagger");
					check(FmumBulletPose.seats(rig.animation,30).size()==3 && FmumBulletPose.seats(rig.animation,1).size()==1,"full/one/empty seated bullet counts");
				}
				continue;
			}
			guns++;
			var manual=new GunReloadState();manual.start(GunReloadState.operate(gun,GunReloadState.Step.CHARGE));
			check(manual.frame(.5F).tilt()==0F,"standalone charge does not apply whole-gun reload tilt");
			for(boolean oldMag:new boolean[]{false,true}) for(boolean chamber:new boolean[]{false,true}) for(boolean special:new boolean[]{false,true}) {
				var plan=GunReloadState.firearm(gun,oldMag,chamber,true,special);
				check(plan.phases().stream().anyMatch(p->p.step()==GunReloadState.Step.UNLOAD_MAG)==oldMag,"out-mag step requires old magazine");
				check(plan.phases().stream().anyMatch(p->p.step()==GunReloadState.Step.CHARGE || p.step()==GunReloadState.Step.RELEASE)==(!special && !chamber),"advanced reload always skips charge, tactical reload preserves chamber");
				if(special) check(plan.phases().stream().anyMatch(p->p.step()==GunReloadState.Step.LOAD_MAG)!=oldMag,"advanced unload and insert are separate operations");
				var clock=new GunReloadState();clock.start(plan);int ticks=0;Set<GunReloadState.Step> events=EnumSet.noneOf(GunReloadState.Step.class);
				while(clock.active()) {
					var f=clock.frame(.5F);var action=GunActionState.Frame.IDLE;
					check(FmumFirstPersonPose.placement(new Matrix4f(),rig.animation,0,0,f.tilt()).isFinite(),"finite phased body");
					check(FmumReloadPose.magazine(new Matrix4f(),rig.animation,f,action).isFinite(),"finite phased mag");
					for(boolean left:new boolean[]{false,true}) check(FmumReloadPose.arm(new Matrix4f(),rig.animation,left,f,action,gun.modelScale).isFinite(),"finite phased hand");
					var completed=clock.tick();if(completed!=GunReloadState.Step.NONE)check(events.add(completed),"each firearm phase completes once");
					ticks++;frames++;
					check(ticks<=plan.duration(),"reload terminates");
				}
				check(ticks==plan.duration(),"pack durations drive clock");
			}
			if(gun.shortName.equals("AK74M")) {
				var f=new GunReloadState.Frame(GunReloadState.Step.UNLOAD_MAG,.5F,1F);
				var m=rig.animation;
				var expected=FmumActionPose.vector(m,"magAttachPoint").clone();var delta=FmumActionPose.vector(m,"magTranslate");
				for(int i=0;i<3;i++) expected[i]+=delta[i]*.5F;
				var origin=FmumReloadPose.magazine(new Matrix4f(),m,f,GunActionState.Frame.IDLE).getTranslation(new org.joml.Vector3f());
				close(origin.x,expected[0],"actual AK mag X path");close(origin.y,expected[1],"actual AK mag Y path");close(origin.z,expected[2],"actual AK mag Z path");
				check(!FmumReloadPose.arm(new Matrix4f(),m,true,f,GunActionState.Frame.IDLE,gun.modelScale).equals(
						FmumActionPose.arm(new Matrix4f(),m,true,GunActionState.Frame.IDLE,gun.modelScale),.001F),"AK operating hand moves with reload magazine");
			}
		}
		check(guns==31 && seated>0,"EFT firearms and seated magazines covered");
		System.out.printf("PASS: %d EFT firearms, %d magazines (%d with visible bullet seating), %d finite body/magazine/hand frames, genuine alternating seating arrays, normal/tactical/advanced unload-only and insert-only plans and phase completion.%n",guns,mags,seated,frames);
	}
}
