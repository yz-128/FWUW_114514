package com.flansmod.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Small additive flash halo, centred on the attachment-resolved muzzle and facing the viewer. */
public final class FmumMuzzleGlow {
	private FmumMuzzleGlow() { }
	public record Frame(Matrix4f pose, float radius, float intensity) { }
	private static final int SEGMENTS = 24;
	private static final float[] RADII = {0F, .18F, .45F, 1F};
	private static final float[][] COLOURS = {{1F,.95F,.68F,.7F}, {1F,.72F,.26F,.35F}, {1F,.42F,.08F,.12F}, {1F,.25F,.015F,0F}};
	private static final float[][] CIRCLE = circle();
	private static float[][] circle() {
		var result = new float[SEGMENTS + 1][2];
		for(int i = 0; i <= SEGMENTS; i++) {
			double angle = i * Math.PI * 2D / SEGMENTS;
			result[i][0] = (float)Math.cos(angle); result[i][1] = (float)Math.sin(angle);
		}
		return result;
	}
	public static Frame frame(Matrix4f gunPose, Matrix4f shaderView, FmumMuzzlePose.Flash flash, float alpha) {
		if(flash == null || flash.model() == null || flash.fade() <= 0F || flash.scale() <= 0F || alpha < 1F/255F)
			return null;
		var muzzle = new Matrix4f(gunPose).mul(flash.mount());
		var position = new Matrix4f(shaderView).mul(muzzle).getTranslation(new Vector3f());
		var scale = muzzle.getScale(new Vector3f());
		float parentScale = (Math.abs(scale.x) + Math.abs(scale.y) + Math.abs(scale.z)) / 3F;
		float radius = .24F * flash.scale() * parentScale;
		// Cancel only the shader view in the emitted vertices. The halo stays a camera-facing
		// plane even when the gun is pitched or the third-person camera swings around it.
		var pose = new Matrix4f(shaderView).invert().translate(position.x, position.y, position.z + .002F);
		return new Frame(pose, radius, Math.clamp(alpha, 0F, 1F));
	}
	public static void emit(VertexConsumer buffer, Frame frame) {
		for(int ring = 0; ring < RADII.length - 1; ring++) for(int i = 0; i < SEGMENTS; i++) {
			vertex(buffer, frame, ring, i); vertex(buffer, frame, ring + 1, i);
			vertex(buffer, frame, ring + 1, i + 1); vertex(buffer, frame, ring, i + 1);
		}
	}
	private static void vertex(VertexConsumer buffer, Frame frame, int ring, int angle) {
		float size = RADII[ring] * frame.radius;
		float[] colour = COLOURS[ring];
		buffer.addVertex(frame.pose, CIRCLE[angle][0] * size, CIRCLE[angle][1] * size, 0F)
				.setColor(colour[0], colour[1], colour[2], colour[3] * frame.intensity);
	}
}
