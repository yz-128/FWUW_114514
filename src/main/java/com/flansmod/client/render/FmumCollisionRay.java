package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.common.types.GunType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Core 1.1's ray in the last non-operating gun frame, before the static OpenGL item shim. */
public final class FmumCollisionRay {
	public record Ray(Vector3f origin,Vector3f direction) { }
	private FmumCollisionRay() { }
	public static Ray local(Model model,GunType gun,float ads,LegacyGunMotion.Frame motion) {
		float[] hold=FmumActionPose.vector(model,"holdingTranslate"),aim=FmumActionPose.vector(model,"aimingTranslate");
		Matrix4f pose=new Matrix4f().translate(hold[0]*(1-ads)+aim[0]*ads,
				hold[1]*(1-ads)+aim[1]*ads+motion.transY(),hold[2]*(1-ads)+aim[2]*ads+motion.transZ());
		Matrix4f rotation=FmumFirstPersonPose.rotation(model,ads,0,0,com.flansmod.common.GunActionState.Frame.IDLE);
		pose.mul(rotation); FmumActionPose.follow(pose,rotation,motion); FmumFirstPersonPose.aim(pose,gun,ads);
		float[] barrel=FmumActionPose.vector(model,"barrelOrigin");
		Vector3f origin=pose.transformPosition(new Vector3f(barrel[0],barrel[1],barrel[2])).sub(-81.5F/160F,.4F,0F);
		Vector3f direction=pose.transformDirection(new Vector3f(1,0,0)).normalize();
		return new Ray(origin,direction);
	}
	public static Ray world(Ray ray,float yaw,float pitch) {
		Matrix4f head=new Matrix4f().rotateY(FmumActionPose.rad(-yaw-90)).rotateZ(FmumActionPose.rad(-pitch));
		return new Ray(head.transformDirection(new Vector3f(ray.origin())),head.transformDirection(new Vector3f(ray.direction())).normalize());
	}
}
