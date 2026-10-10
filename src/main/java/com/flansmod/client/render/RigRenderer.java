package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a {@link BakedRig} through a modern vertex consumer.
 *
 * <p>1.7.10 drew each polygon with immediate-mode OpenGL, the texture bound and the light level
 * set. 1.21 has neither; geometry goes into a {@link VertexConsumer} as triangles and the pose
 * comes from a {@link PoseStack}. The translation is mechanical — each quad becomes two triangles
 * with the same winding, the same UVs and the same normal — so this class is deliberately small.
 */
public final class RigRenderer
{
	private RigRenderer() { }

	/**
	 * Emits every quad of a rig.
	 *
	 * <p>The geometry goes down exactly as the pack modelled it. 1.7.10 drew the same vertices in
	 * the same space, and the item model's {@code display} block supplies every orientation the
	 * game needs, so nothing extra is applied here.
	 *
	 * @param pose   the current pose; it is not modified
	 * @param buffer where the triangles go
	 * @param light  packed light value
	 * @param overlay packed overlay value
	 * @param red    tint, applied to every vertex
	 */
	public static void render(PoseStack pose, VertexConsumer buffer, BakedRig rig,
							  int light, int overlay, float red, float green, float blue, float alpha)
	{
		render(pose, buffer, rig, light, overlay, red, green, blue, alpha, false);
	}

	/**
	 * Emits one part's quads, for a renderer that moves parts independently.
	 *
	 * <p>The full-rig path is what most items use; guns need this one because a slide, a pump and a
	 * clip each move under their own transform, and drawing them all in one pass would weld them
	 * to the receiver.
	 */
	public static void renderPart(PoseStack pose, VertexConsumer buffer, List<BakedRig.Quad> quads,
								  int light, int overlay, float red, float green, float blue, float alpha,
								  boolean mirrored)
	{
		Matrix4f matrix = pose.last().pose();
		for(BakedRig.Quad quad : quads)
			emit(matrix, buffer, quad, light, overlay, red, green, blue, alpha, mirrored);
	}

	/**
	 * Emits every quad of a rig, for a pose that mirrors the geometry.
	 *
	 * <p>A negative scale turns the model inside out: what was a front face now faces backwards,
	 * so the same counter-clockwise winding would be culled away and the model would disappear.
	 * Reversing the vertex order of each quad restores the facing. 1.7.10 could ignore this
	 * because a {@code glScalef(-1,…)} flips the winding the fixed-function pipeline sees, but a
	 * {@link VertexConsumer} takes the order as given.
	 *
	 * @param mirrored true when the current pose contains an odd number of negative scales
	 */
	public static void render(PoseStack pose, VertexConsumer buffer, BakedRig rig,
							  int light, int overlay, float red, float green, float blue, float alpha,
							  boolean mirrored)
	{
		Matrix4f matrix = pose.last().pose();

		for(BakedRig.Quad quad : rig.quads)
			emit(matrix, buffer, quad, light, overlay, red, green, blue, alpha, mirrored);
	}

	/** One quad as two triangles, in the order the old renderer wound it. */
	private static void emit(Matrix4f matrix, VertexConsumer buffer, BakedRig.Quad quad,
							 int light, int overlay, float red, float green, float blue, float alpha,
							 boolean mirrored)
	{
		float[][] p = quad.positions();
		float[][] uv = quad.uvs();

		if(mirrored)
		{
			vertex(matrix, buffer, p[3], uv[3], quad, light, overlay, red, green, blue, alpha);
			vertex(matrix, buffer, p[2], uv[2], quad, light, overlay, red, green, blue, alpha);
			vertex(matrix, buffer, p[1], uv[1], quad, light, overlay, red, green, blue, alpha);
			vertex(matrix, buffer, p[0], uv[0], quad, light, overlay, red, green, blue, alpha);
			return;
		}

		vertex(matrix, buffer, p[0], uv[0], quad, light, overlay, red, green, blue, alpha);
		vertex(matrix, buffer, p[1], uv[1], quad, light, overlay, red, green, blue, alpha);
		vertex(matrix, buffer, p[2], uv[2], quad, light, overlay, red, green, blue, alpha);
		vertex(matrix, buffer, p[3], uv[3], quad, light, overlay, red, green, blue, alpha);
	}

	/**
	 * Emits a quad as a strip when the consumer supports it, which is how the game draws item
	 * models. Four vertices in winding order is a quad for the purposes of the model formats; the
	 * buffer turns it into triangles.
	 */
	private static void vertex(Matrix4f matrix, VertexConsumer buffer, float[] pos, float[] uv,
							   BakedRig.Quad quad, int light, int overlay,
							   float red, float green, float blue, float alpha)
	{
		buffer.addVertex(matrix, pos[0], pos[1], pos[2])
				.setColor(red, green, blue, alpha)
				.setUv(uv[0], uv[1])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(quad.nx(), quad.ny(), quad.nz());
	}
}
