package com.flansmod.client.model;

import com.flansmod.common.GunStats;
import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.client.render.FmumActionPose;
import org.joml.Matrix4f;
import java.util.random.RandomGenerator;

/** Six-axis model recoil and camera recovery from EFT's FMUM 1.1 core. */
public final class FmumRecoil {
	// X/Y/Z rotation, X/Y/Z translation.
	private final float[] now=new float[6],last=new float[6],retention=new float[6],shake=new float[6];
	private float antiShake=.36F,camera;
	public record Frame(float rx,float ry,float rz,float tx,float ty,float tz) {
		public static final Frame ZERO=new Frame(0,0,0,0,0,0);
		public Matrix4f rotation() { return new Matrix4f().rotateX(FmumActionPose.rad(rx)).rotateY(FmumActionPose.rad(ry)).rotateZ(FmumActionPose.rad(rz)); }
	}
	public void shoot(GunStats.Shot shot,Model model) {
		var g=shot.gun();var v=shot.stats();float p=shot.pitch(),y=shot.yaw();
		now[0]+=y*g.modelRecoil[2];now[1]+=y*v.rotY();now[2]+=p*v.rotZ();
		now[3]+=p*v.transXPitch()+Math.abs(y)*v.transXYaw();
		now[4]+=p*g.modelRecoil[6];now[5]+=y*g.modelRecoil[7];
		int[] order={2,1,0,3,4,5};for(int i=0;i<6;i++) retention[i]=g.antiModelRecoil[order[i]];
		String[] fields={"RotX","RotY","RotZ","TransX","TransY","TransZ"};
		float[] defaults={2F,.8F,.8F,.04F,.01F,.01F};float crouch=shot.crouching()?.8F:1F;
		for(int i=0;i<6;i++) shake[i]=FmumActionPose.scalar(model,"recoilShake"+fields[i],defaults[i])*crouch;
		antiShake=FmumActionPose.scalar(model,"antiRecoilShakeConst",.36F)*crouch;camera=v.camera();
	}
	public void tick() { for(int i=0;i<6;i++) { last[i]=now[i];now[i]*=retention[i];shake[i]*=antiShake; } }
	public Frame frame(float partial,RandomGenerator random) {
		float[] v=new float[6];for(int i=0;i<6;i++) v[i]=last[i]+(now[i]-last[i])*partial;
		v[3]=-v[3]; // RenderGun negates X before adding per-frame shake.
		for(int i=0;i<6;i++) if(random!=null) v[i]+=shake[i]*(random.nextFloat()-.5F);
		return new Frame(v[0],v[1],v[2],v[3],v[4],v[5]);
	}
	public float fov(float partial) { return camera*(last[3]+(now[3]-last[3])*partial); }
	public void reset() { java.util.Arrays.fill(now,0);java.util.Arrays.fill(last,0);java.util.Arrays.fill(shake,0);camera=0; }
	public static final class Camera {
		private float pitch,yaw,antiPitch,antiYaw;
		public void shoot(float p,float y) { pitch+=p;yaw+=y; }
		public record Delta(float pitch,float yaw) { }
		public Delta tick() {
			if(pitch>0F) pitch*=.8F;
			antiPitch+=pitch;antiYaw+=yaw;
			var delta=new Delta(-pitch+antiPitch*.2F,-yaw+antiYaw*.2F);
			antiPitch*=.8F;antiYaw*=.8F;return delta;
		}
		public void reset() { pitch=yaw=antiPitch=antiYaw=0F; }
	}
}
