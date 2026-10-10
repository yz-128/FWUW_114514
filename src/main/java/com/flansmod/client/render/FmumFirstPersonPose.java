package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry;
import com.flansmod.common.types.ModifiableType;
import org.joml.Matrix4f;

/** FMUM 1.7.10's gun-local pose. Shared with the offline eye-view preview. */
public final class FmumFirstPersonPose
{
	private FmumFirstPersonPose() { }

	/** Vanilla ItemRenderer, then ForgeHooksClient.renderEquippedItem (helper=false). */
	public static Matrix4f itemFrame(Matrix4f pose)
	{
		return equippedFrame(pose.rotateY(rad(45F)).scale(0.4F));
	}

	/** Forge 1.7.10 uses this same helper for EQUIPPED and EQUIPPED_FIRST_PERSON. */
	public static Matrix4f equippedFrame(Matrix4f pose)
	{
		return pose.translate(0F, -0.3F, 0F).scale(1.5F)
				.rotateY(rad(50F)).rotateZ(rad(-25F)).translate(-0.9375F, -0.0625F, 0F);
	}

	public static boolean hasPose(ModelGeometry.Model model)
	{
		return model != null && (model.fmumPose.containsKey("holdingTranslate")
				|| model.fmumPose.containsKey("holdingRotate")
				|| model.fmumPose.containsKey("aimingTranslate"));
	}

	/** After vanilla's Ry(45) / S(0.4), before recoil and the sight pivot. */
	public static Matrix4f placement(Matrix4f pose, ModelGeometry.Model model,
			float ads, float sprint, float reload)
	{
		return placement(pose,model,ads,sprint,reload,com.flansmod.common.GunActionState.Frame.IDLE,LegacyGunMotion.Frame.ZERO);
	}
	public static Matrix4f placement(Matrix4f pose, ModelGeometry.Model model,
			float ads, float sprint, float reload, com.flansmod.common.GunActionState.Frame action, LegacyGunMotion.Frame motion)
	{
		return placement(pose,model,ads,sprint,reload,action,motion,com.flansmod.client.model.FmumRecoil.Frame.ZERO);
	}
	public static Matrix4f placement(Matrix4f pose, ModelGeometry.Model model,
			float ads, float sprint, float reload, com.flansmod.common.GunActionState.Frame action, LegacyGunMotion.Frame motion,
			com.flansmod.client.model.FmumRecoil.Frame recoil)
	{
		if(!hasPose(model))
			return pose.rotateZ(rad(25F)).rotateY(rad(-5F)).translate(0.15F, 0.2F, -0.6F);

		pose.rotateZ(rad(20F)).rotateY(rad(-5F)).translate(0.15F, 0.375F, -1.005F)
				.rotateX(rad(0.5F)).rotateZ(rad(5F)).translate(0F, 0.7F / 160F - 0.03F, -0.35F / 160F);
		float[] trans = blend(model, "Translate", ads, sprint, reload);
		float[] rot = blend(model, "Rotate", ads, sprint, 0F);
		FmumActionPose.addOperation(trans,model,"Translate",sprint,action);
		FmumActionPose.addOperation(rot,model,"Rotate",sprint,action);
		trans[1]+=motion.transY(); trans[2]+=motion.transZ();
		trans[0]+=recoil.tx();trans[1]+=recoil.ty();trans[2]+=recoil.tz();
		return pose.translate(trans[0], trans[1], trans[2])
				.rotateX(rad(rot[0])).rotateY(rad(rot[1])).rotateZ(rad(rot[2])).mul(recoilRotation(model,reload,recoil));
	}
	public static Matrix4f recoilRotation(ModelGeometry.Model model,float reload,com.flansmod.client.model.FmumRecoil.Frame recoil) {
		float[] r=FmumActionPose.vector(model,"reloadRotate");
		return new Matrix4f().rotateX(rad(recoil.rx()+r[0]*reload)).rotateY(rad(recoil.ry()+r[1]*reload)).rotateZ(rad(recoil.rz()+r[2]*reload));
	}
	public static Matrix4f rotation(ModelGeometry.Model model,float ads,float sprint,float reload,com.flansmod.common.GunActionState.Frame action) {
		float[] rot=blend(model,"Rotate",ads,sprint,0F);
		FmumActionPose.addOperation(rot,model,"Rotate",sprint,action);
		return FmumActionPose.rotate(new Matrix4f(),rot).mul(recoilRotation(model,reload,com.flansmod.client.model.FmumRecoil.Frame.ZERO));
	}

	/** EFT core RenderGun bytecode 3093..3207: translate, rotate, undo the pivot. */
	public static Matrix4f aim(Matrix4f pose, ModifiableType type, float ads)
	{
		if(type instanceof com.flansmod.common.types.GunType gun)
		{
			LegacySightPose.Center sight = LegacySightPose.forGun(gun);
			return aim(pose, sight, ads);
		}
		return aim(pose, type.aimCenterX, type.aimCenterY, type.aimCenterZ, ads);
	}

	public static Matrix4f aim(Matrix4f pose, LegacySightPose.Center sight, float ads)
	{
		return aim(pose, sight.roll(), sight.y(), sight.z(), ads);
	}

	private static Matrix4f aim(Matrix4f pose, float roll, float y, float z, float ads)
	{
		return pose.translate(0F, (0.4F - y) * ads + y, -z * ads + z)
				.rotateX(rad(-roll * ads)).translate(0F, -y, -z);
	}

	private static float[] blend(ModelGeometry.Model model, String suffix, float ads, float sprint, float reload)
	{
		float[] result = new float[3];
		add(result, model.fmumPose.get("holding" + suffix), 1F - ads);
		add(result, model.fmumPose.get("aiming" + suffix), ads);
		add(result, model.fmumPose.get("sprint" + suffix), sprint);
		add(result, model.fmumPose.get("reload" + suffix), reload);
		return result;
	}

	private static void add(float[] target, float[] value, float weight)
	{
		if(value != null)
			for(int i = 0; i < 3; i++) target[i] += value[i] * weight;
	}

	private static float rad(float degrees) { return (float)Math.toRadians(degrees); }
}
