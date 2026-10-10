package com.flansmod.client.render;

import org.joml.Matrix4f;

/** 1.7.10 RenderPlayer -> Forge helper -> FMUM RenderGun EQUIPPED. */
public final class LegacyThirdPersonPose
{
	private LegacyThirdPersonPose() { }

	/** Replace the modern ItemInHandLayer frame, after the player's arm transform. */
	public static Matrix4f handFrame(Matrix4f pose, boolean leftHand, boolean blocking)
	{
		float sign = leftHand ? -1F : 1F;
		pose.translate(-sign / 16F, -0.125F, 0.625F)
				.rotateY(rad(180F)).rotateX(rad(90F));
		if(leftHand) pose.scale(-1F, 1F, 1F);
		// ItemGun.isFull3D() is true. The vanilla bow-item branch does not apply to guns:
		// it tests Items.bow itself, independently of the player's raised bow arm pose.
		pose.translate(-1F / 16F, 7F / 16F, 1F / 16F);
		if(blocking) pose.translate(0.05F, 0F, -0.1F)
				.rotateY(rad(-50F)).rotateX(rad(-10F)).rotateZ(rad(-60F));
		pose.translate(0F, 0.1875F, 0F).scale(0.625F, -0.625F, 0.625F)
				.rotateX(rad(-100F)).rotateY(rad(45F));
		return FmumFirstPersonPose.equippedFrame(pose);
	}

	/** EFT core RenderGun's EQUIPPED branch; offset is applied exactly once. */
	public static Matrix4f placement(Matrix4f pose, float x, float y, float z)
	{
		return pose.rotateZ(rad(35F)).rotateY(rad(-5F)).translate(0.75F, -0.22F, -0.08F)
				.scale(1F, 1F, -1F).translate(x, y, z);
	}

	private static float rad(float degrees) { return (float)Math.toRadians(degrees); }
}
