package com.flansmod.client.model;

import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.client.render.FmumActionPose;
import com.flansmod.client.render.FmumMuzzlePose;
import com.flansmod.common.types.BulletType;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** EFT GunCase: world gravity, per-tick spin, interpolation, and its finite model-defined lifetime. */
public final class FmumCasing {
	public final BulletType type;
	public final Vector3f position=new Vector3f(),lastPosition=new Vector3f(),velocity,center,axis;
	public final Matrix4f rotation;
	public final int lifetime;
	public float spin,lastSpin,spinVelocity;
	public int age;
	public FmumCasing(BulletType type,Model model,Matrix4f gun,boolean manual,long seed,float roll) {
		this.type=type;
		var random=new Random(seed);
		var direction=new Matrix4f(gun).normalize3x3();
		// Actual EFT RenderGun overwrites preSet's WC vectors with these normal vectors,
		// even during manual charge. Only rotVelocityWC survives that overwrite.
		velocity=direction.transformDirection(sample(model,"caseEjectVelocity","caseEjectRandV",
				new float[]{-.025F,.025F,.25F},new float[]{.075F,.05625F,.03125F},random));
		axis=direction.transformDirection(sample(model,"caseEjectAngularV","caseEjectRandAV",
				new float[]{-.3F,-.7F,-.3F},new float[]{.6F,-.8F,.6F},random));
		center=direction.transformDirection(new Vector3f(type.bulletRotCenter).mul(type.bulletScale/16F));
		rotation=FmumMuzzlePose.smokeOrigin(gun,roll).setTranslation(0,0,0);
		spinVelocity=FmumActionPose.scalar(model,manual?"rotVelocityWC":"rotVelocity",manual?30F:55F);
		lifetime=Math.max(1,(int)FmumActionPose.scalar(model,"caseTime",30F));
	}
	private static Vector3f sample(Model model,String base,String spread,float[] fallback,float[] range,Random random) {
		float[] a=model==null?fallback:model.fmumPose.getOrDefault(base,fallback);
		float[] b=model==null?range:model.fmumPose.getOrDefault(spread,range);
		return new Vector3f(a[0]+b[0]*random.nextFloat(),a[1]+b[1]*random.nextFloat(),a[2]+b[2]*random.nextFloat());
	}
	public static Vector3f origin(Model model,Matrix4f gun) {
		return gun.transformPosition(new Vector3f(FmumActionPose.vector(model,"caseOrigin")));
	}
	public void tick() {
		if(!alive())return;
		age++;lastPosition.set(position);
		// The actual 1.1 jar uses these constants, even when ModelGun exposes friction/gravity fields.
		velocity.y+=-1.96F/160F;velocity.mul(.99F);position.add(velocity);
		spinVelocity*=.99F;lastSpin=spin;spin+=spinVelocity;
	}
	public boolean alive() {return age<lifetime;}
	public Matrix4f pose(float partial) {
		float p=Math.clamp(partial,0F,1F);
		var location=new Vector3f(lastPosition).lerp(position,p).add(center);
		var result=new Matrix4f().translate(location);
		if(axis.lengthSquared()>1E-12F)result.rotate(FmumActionPose.rad(lastSpin+(spin-lastSpin)*p),new Vector3f(axis).normalize());
		return result.translate(-center.x,-center.y,-center.z).mul(rotation).scale(type.bulletScale);
	}
}
