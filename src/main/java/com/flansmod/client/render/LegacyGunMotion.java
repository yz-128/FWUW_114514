package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry.Model;
import static com.flansmod.client.render.FmumActionPose.scalar;

/** FMUM's view inertia, vertical acceleration, breathing and walking; no Minecraft input. */
public final class LegacyGunMotion {
	public record Frame(float transY,float transZ,float rotX,float rotYaw,float rotPitch) {
		public static final Frame ZERO=new Frame(0,0,0,0,0);
	}
	private float x,yaw,pitch,vertical,oldX,oldYaw,oldPitch,oldVertical;
	private float breathY,breathZ,walkY,walkZ,oldBreathY,oldBreathZ,oldWalkY,oldWalkZ,phase;
	public void reset() { x=yaw=pitch=vertical=oldX=oldYaw=oldPitch=oldVertical=0F; breathY=breathZ=walkY=walkZ=oldBreathY=oldBreathZ=oldWalkY=oldWalkZ=phase=0F; }
	public void tick(Model m,float deltaYaw,float deltaPitch,float deltaVelocityY,float walked,float bob,boolean aiming) {
		oldX=x;oldYaw=yaw;oldPitch=pitch;oldVertical=vertical;
		x=(x+deltaYaw*scalar(m,"inertiaXConst",.1F))*scalar(m,"inertiaXMult",.8F);
		yaw=(yaw+deltaYaw*scalar(m,"inertiaYConst",-.1F))*scalar(m,"inertiaYMult",.8F);
		pitch=(pitch+deltaPitch*scalar(m,"inertiaZConst",-.1F))*scalar(m,"inertiaZMult",.8F);
		vertical=(vertical+deltaVelocityY*scalar(m,aiming?"aimGravityConst":"gravityConst",aiming?.05F:.15F))
				*scalar(m,aiming?"aimGravityMult":"gravityMult",.8F);
		oldBreathY=breathY;oldBreathZ=breathZ;oldWalkY=walkY;oldWalkZ=walkZ;
		phase++;
		breathY=(float)Math.sin(phase*scalar(m,"breathShakeCycleY",.07F)+scalar(m,"breathShakePhaseY",0F));
		breathZ=(float)Math.sin(phase*scalar(m,"breathShakeCycleZ",.035F)+scalar(m,"breathShakePhaseZ",0F));
		float cycle=walked*scalar(m,"walkShakeCycle",-(float)Math.PI);
		walkZ=(float)Math.sin(cycle)*bob;walkY=((float)Math.abs(Math.cos(cycle))-.5F)*bob;
	}
	public Frame frame(Model m,float partial,float ads,float sprint) {
		float by=lerp(oldBreathY,breathY,partial),bz=lerp(oldBreathZ,breathZ,partial),wy=lerp(oldWalkY,walkY,partial),wz=lerp(oldWalkZ,walkZ,partial);
		return new Frame(lerp(oldVertical,vertical,partial)+by*scalar(m,"breathShakeAmplitudeY",.0012F)+wy*weight(m,"walkShakeAmplitudeY",new float[]{.1F,.2F,-.8F},ads,sprint),
				bz*scalar(m,"breathShakeAmplitudeZ",.0012F)+wz*weight(m,"walkShakeAmplitudeZ",new float[]{.1F,.2F,.8F},ads,sprint),
				lerp(oldX,x,partial)+wz*weight(m,"walkShakeRotMultX",new float[]{-5,-10,-40},ads,sprint),
				lerp(oldYaw,yaw,partial)+bz*scalar(m,"breathShakeRotMultY",.1F)+wz*weight(m,"walkShakeRotMultY",new float[]{-5,-10,-40},ads,sprint),
				lerp(oldPitch,pitch,partial)+by*scalar(m,"breathShakeRotMultZ",-.1F)+wy*weight(m,"walkShakeRotMultZ",new float[]{5,10,-40},ads,sprint));
	}
	public float cameraYaw(Model m,float ads,float sprint) {
		return (breathZ-oldBreathZ)*scalar(m,"cameraYawBreathShakeMult",-.2F)+(walkZ-oldWalkZ)*weight(m,"cameraWalkShakeMultYaw",new float[]{7,7,5},ads,sprint);
	}
	public float cameraPitch(Model m,float ads,float sprint) {
		return (breathY-oldBreathY)*scalar(m,"cameraPitchBreathShakeMult",.2F)+(walkY-oldWalkY)*weight(m,"cameraWalkShakeMultPitch",new float[]{-7,-7,-5},ads,sprint);
	}
	public static float weight(Model m,String key,float[] fallback,float ads,float sprint) {
		float[] a=m==null?fallback:m.fmumArrays.getOrDefault(key,fallback);
		return a.length<3?0F:a[0]*ads+a[2]*sprint+a[1]*(2F-ads-sprint)*.5F;
	}
	private static float lerp(float from,float to,float p) { return from+(to-from)*p; }
}
