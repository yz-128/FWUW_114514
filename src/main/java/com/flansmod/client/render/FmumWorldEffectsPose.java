package com.flansmod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionfc;

/** Conversion at the boundary between item poses, world positions and the shader view. */
public final class FmumWorldEffectsPose {
	private FmumWorldEffectsPose() { }

	public static Matrix4f gunToCameraRelativeWorld(Matrix4f gunPose, Quaternionfc camera, Matrix4f shaderView) {
		return new Matrix4f().rotate(camera).mul(shaderView).mul(gunPose);
	}

	public static Matrix4f worldStage(Matrix4f stageView, Matrix4f shaderView) {
		// LevelRenderer already puts the camera view into RenderSystem's shader matrix.
		// Supplying stageView again in the vertices rotates world effects around the camera
		// twice, visibly detaching smoke and casings in third person.
		return new Matrix4f(shaderView).invert().mul(stageView);
	}
}
