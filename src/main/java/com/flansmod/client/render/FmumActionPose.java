package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.common.GunActionState.Frame;
import com.flansmod.common.GunActionState.Operation;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/** FMUM 1.1 operation transforms, shared by rendering and offline verification. */
public final class FmumActionPose {
	private FmumActionPose() { }
	public static float scalar(Model m, String key, float fallback) { return m == null ? fallback : m.fmumScalars.getOrDefault(key, fallback); }
	public static float[] vector(Model m, String key) { return m == null ? new float[3] : m.fmumPose.getOrDefault(key, new float[3]); }
	public static float tilt(Model m, Frame f) {
		return switch(f.operation()) {
			case CHECK_AMMO -> scalar(m, "checkAmmoTiltTimeRatio", 1F/6F);
			case CHECK_CHAMBER -> scalar(m, "checkChamberTiltTimeRatio", 1F/3F);
			case CHECK_CHAMBER_BC -> scalar(m, "checkChamberTiltBCTimeRatio", .5F);
			default -> 1F;
		};
	}
	public static float ads(Model m, Frame f, float ads) {
		return switch(f.operation()) {
			case CHECK_AMMO, CHECK_CHAMBER, CHECK_CHAMBER_BC -> ads * Math.max(0F, 1F - f.progress() / Math.max(.00001F, tilt(m,f)));
			default -> ads;
		};
	}
	public static void addOperation(float[] value, Model m, String suffix, float sprint, Frame f) {
		add(value, vector(m, "takeOut" + suffix), f.takeOut());
		String key = switch(f.operation()) {
			case CHECK_AMMO -> "checkAmmo";
			case CHECK_CHAMBER, CHECK_CHAMBER_BC -> "checkChamber";
			case REST, UNREST -> "rest";
			default -> null;
		};
		if(key != null) {
			float weight = key.equals("rest") ? f.progress() * (1F - sprint) : Math.min(1F, f.progress()/Math.max(.00001F,tilt(m,f)));
			add(value, vector(m,key+suffix), weight);
		}
	}
	private static void add(float[] target, float[] v, float weight) { for(int i=0;i<3;i++) target[i] += v[i]*weight; }
	public static Matrix4f rotate(Matrix4f pose, float[] v) {
		return pose.rotateX(rad(v[0])).rotateY(rad(v[1])).rotateZ(rad(v[2]));
	}
	public static Matrix4f translate(Matrix4f pose, float[] v, float scale) { return pose.translate(v[0]*scale,v[1]*scale,v[2]*scale); }
	public static float rad(float v) { return (float)Math.toRadians(v); }
	/** The 1.1 core keeps the one-stage curve un-clamped after checkAmmoEndTime. */
	public static Matrix4f magazine(Matrix4f pose, Model m, Frame f) {
		float[] position = vector(m,"magAttachPoint").clone(), rotation = vector(m,"magAttachRotate").clone();
		if(f.operation() == Operation.CHECK_AMMO && f.progress() > tilt(m,f)) {
			float p=f.progress(), start=tilt(m,f), middle=scalar(m,"checkAmmoMagTimeRatio",1F/3F), end=scalar(m,"checkAmmoEndTime",2F/3F);
			float first, second=0F;
			if(middle == 0F) first=(p-start)/Math.max(.00001F,end-start);
			else if(p < middle) first=(p-start)/Math.max(.00001F,middle-start);
			else { first=1F; second=p<end ? (p-middle)/Math.max(.00001F,end-middle) : 1F; }
			add(position,vector(m,"checkAmmoMagTranslate"),first); add(position,vector(m,"checkAmmoMagTranslate2"),second);
			add(rotation,vector(m,"checkAmmoMagRotate"),first); add(rotation,vector(m,"checkAmmoMagRotate2"),second);
		}
		return rotate(translate(pose,position,1F), rotation);
	}
	public record Charge(float handle, float pre, float slide, float preRotation) { }
	public static Charge charge(Model m, Frame f) {
		if(f.operation()!=Operation.CHECK_CHAMBER || f.progress()<tilt(m,f)) return new Charge(0F,0F,0F,0F);
		float start=tilt(m,f), end=scalar(m,"checkChamberChargeTimeRatio",2F/3F);
		float p=Math.min(1F,(f.progress()-start)/Math.max(.00001F,end-start))*scalar(m,"checkChamberChargeRatio",.5F);
		return chargeProgress(m,p);
	}
	public static Charge chargeProgress(Model m,float p) {
		float preTime=scalar(m,"prechargeTime",0F), handle, pre, rotation;
		if(p<preTime) { rotation=p/preTime; pre=-scalar(m,"prechargeDistance",0F)*rotation; handle=0F; }
		else { rotation=1F; pre=-scalar(m,"prechargeDistance",0F); handle=-scalar(m,"chargeHandleDistance",0F)*(p-preTime)/Math.max(.00001F,1F-preTime); }
		return new Charge(handle,pre,handle+pre,rotation);
	}
	public static float slide(Model m, Frame f, float recoilSlide, boolean caught) {
		if(f.operation()==Operation.CHECK_CHAMBER && f.progress()>=tilt(m,f)) return charge(m,f).slide();
		return caught ? Math.min(recoilSlide,-scalar(m,"slideLockDistance",0F)) : recoilSlide;
	}
	public static Matrix4f pump2(Matrix4f pose, Model m, Frame f, float scale, boolean withHandle) {
		return pump2(pose,m,charge(m,f),scale,withHandle);
	}
	public static Matrix4f pump2(Matrix4f pose,Model m,Charge c,float scale,boolean withHandle) {
		float[] pivot=vector(m,"prechargeRotatePoint"), rot=vector(m,"prechargeRotate").clone();
		pose.translate((c.pre()+(withHandle?c.handle():0F)+pivot[0])*scale,pivot[1]*scale,pivot[2]*scale);
		for(int i=0;i<3;i++) rot[i]*=c.preRotation();
		return translate(rotate(pose,rot),pivot,-scale);
	}
	public static Matrix4f arm(Matrix4f pose, Model m, boolean left, Frame f, float gunScale) {
		return arm(pose,m,left,f,gunScale,null);
	}
	public static Matrix4f arm(Matrix4f pose, Model m, boolean left, Frame f, float gunScale, com.flansmod.common.types.GunType gun) {
		String hand=left?"left":"right", pos=hand+"ArmPos", rot=hand+"ArmRot";
		if(scalar(m,hand+"HandAmmo",0F)!=0F && f.operation()==Operation.CHECK_AMMO && f.progress()>tilt(m,f)) {
			magazine(pose,m,f); translate(pose,vector(m,"magAttachPoint"),-1F);
			pos=hand+"ArmAmmoPos"; rot=hand+"ArmAmmoRot";
		} else if(scalar(m,hand+"HandCheckChamber",0F)!=0F && f.operation()==Operation.CHECK_CHAMBER && f.progress()>tilt(m,f)) {
			pump2(pose,m,f,gunScale,true); pos=hand+"HandCheckChamberPos"; rot=hand+"HandCheckChamberRot";
		}
		if(pos.equals(hand+"ArmPos"))return FmumHoldingArmPose.arm(pose,gun,m,left);
		rotate(translate(pose,vector(m,pos),1F),vector(m,rot));
		float[] scale=m.fmumPose.get(hand+"ArmScale");
		return scale==null?pose:pose.scale(scale[0],scale[1],scale[2]);
	}
	/** The inverse basis is captured once, as getReverse*NormalVec does in the old renderer. */
	public static Matrix4f follow(Matrix4f pose, Matrix4f localRotation, LegacyGunMotion.Frame motion) {
		Matrix3f inverse=new Matrix3f(localRotation).invert();
		Vector3f axis=new Vector3f();
		inverse.transform(axis.set(1,0,0)).normalize(); pose.rotate(rad(motion.rotX()),axis);
		inverse.transform(axis.set(0,1,0)).normalize(); pose.rotate(rad(motion.rotYaw()),axis);
		inverse.transform(axis.set(0,0,1)).normalize(); pose.rotate(rad(motion.rotPitch()),axis);
		return pose;
	}
}
