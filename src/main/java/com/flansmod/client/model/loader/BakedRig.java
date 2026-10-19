package com.flansmod.client.model.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link ModelGeometry.Model} into quads that can be handed to a vertex consumer.
 *
 * <p>This is where the old renderer's conventions are reproduced, and every table below is
 * carried over from the offline converter that was verified against the whole 1.7.10 corpus —
 * the vertex order, the per-face UV rectangles and the corner offsets all have to match exactly
 * or a texture lands on the wrong face.
 *
 * <p>Three things happen here that the old code did at draw time:
 * <ul>
 *   <li>the 1/16 model-to-world scale (1.7.10 passed {@code worldScale = 0.0625} to
 *       {@code render}), combined with the definition's {@code ModelScale};</li>
 *   <li>the per-part rotation ({@code rotateAngle*} + rotation point), which is baked in because
 *       a static pose costs nothing and avoids a transform stack per part;</li>
 *   <li>{@code flipAll()}, which mirrored the model on Y and Z.</li>
 * </ul>
 */
public final class BakedRig
{
	/** One face: four positions, four UVs, and the direction it faces. */
	public record Quad(float[][] positions, float[][] uvs, float nx, float ny, float nz)
	{
	}

	public final List<Quad> quads = new ArrayList<>();

	/**
	 * The same quads, grouped by the model field they came from ({@code gunModel}, {@code slideModel},
	 * {@code pumpModel}, …).
	 *
	 * <p>RenderGun animated the moving parts by drawing them under their own transform — the slide
	 * slid, the pump travelled, the clip tilted — so the parts have to stay separable after baking.
	 * {@link #quads} is kept as the flat list for everything that draws the whole gun at once.
	 */
	public final Map<String, List<Quad>> quadsByPart = new LinkedHashMap<>();
	/** Renderer-array elements for multi-point reticles; selecting a dot must not repeat the whole mesh. */
	public final Map<String, List<Quad>> quadsByRenderer = new LinkedHashMap<>();

	public float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
	public float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
	/** Prisms could not be baked; reported so the log does not silently understate the model. */
	public int skippedPrisms;

	/**
	 * The model's own placement offsets, carried through from the class.
	 *
	 * <p>These are applied by the renderer at draw time rather than baked into the vertices,
	 * because they differ per display context: {@code thirdPersonOffset} is added only in third
	 * person, and {@code itemFrameOffset} only for dropped items. Baking them would displace every
	 * other context by the same amount.
	 */
	public float thirdPersonOffsetX, thirdPersonOffsetY, thirdPersonOffsetZ;
	public float itemFrameOffsetX, itemFrameOffsetY, itemFrameOffsetZ;
	public float stanceRotateX, stanceRotateY, stanceRotateZ;
	public float stanceTranslateX, stanceTranslateY, stanceTranslateZ;
	/** Whether the gun declares a stance at all; a model without one keeps the plain resting pose. */
	public boolean fancyStance;

	/**
	 * The gun's animation parameters, copied straight through from the model class.
	 *
	 * <p>Kept as a reference rather than flattened into fields, because unlike the offsets above
	 * they are read only by the animation code and there are two dozen of them — one object is
	 * easier to thread through than two dozen parameters, and it keeps the bake a faithful copy of
	 * what the class declared.
	 */
	public ModelGeometry.Model animation;

	public float width() { return maxX - minX; }
	public float height() { return maxY - minY; }
	public float depth() { return maxZ - minZ; }

	// ---------------------------------------------------------------- the old renderer's tables

	/**
	 * M-index to the port's bit-index vertex numbering ({@code x + y*2 + z*4}).
	 * The old renderer and the modern one disagree on corner order; this is the map between them.
	 */
	static final int[] M2BIT = { 0, 1, 3, 2, 4, 5, 7, 6 };

	/**
	 * The four M-vertices of each of the six legacy polys, in the order that matches the UV quad
	 * corners {@code {u1,v0}, {u0,v0}, {u0,v1}, {u1,v1}}.
	 */
	static final int[][] POLY_M = {
			{ 5, 1, 2, 6 },   // 0: +x
			{ 0, 4, 7, 3 },   // 1: -x
			{ 5, 4, 0, 1 },   // 2: -y
			{ 2, 3, 7, 6 },   // 3: +y
			{ 1, 0, 3, 2 },   // 4: -z
			{ 4, 5, 6, 7 },   // 5: +z
	};
	static final String[] POLY_DIR = { "east", "west", "down", "up", "north", "south" };
	static final float[][] POLY_NORMAL = {
			{ 1, 0, 0 }, { -1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 },
	};

	/** The four vertex slots of each face, in the order a quad must be wound. */
	static final Map<String, int[]> FACE_SLOTS = new LinkedHashMap<>();
	static {
		FACE_SLOTS.put("up", new int[] { 7, 3, 2, 6 });
		FACE_SLOTS.put("down", new int[] { 5, 4, 0, 1 });
		FACE_SLOTS.put("north", new int[] { 0, 2, 3, 1 });
		FACE_SLOTS.put("south", new int[] { 5, 7, 6, 4 });
		FACE_SLOTS.put("west", new int[] { 4, 6, 2, 0 });
		FACE_SLOTS.put("east", new int[] { 1, 3, 7, 5 });
	}

	// ---------------------------------------------------------------- baking

	public static BakedRig bake(ModelGeometry.Model model, float modelScale)
	{
		return bake(model, modelScale, 1F, 1F);
	}

	/**
	 * Bakes a model, optionally correcting its texture coordinates.
	 *
	 * <p>A model class hardcodes the texture size it was exported against and bakes its UVs against
	 * that size. When the pack then ships a texture of a different size — which it frequently does,
	 * typically the same art at a higher resolution — the model's UVs no longer point at the right
	 * pixels and the gun samples the wrong region of its skin.
	 *
	 * <p>The correction is a scale of the normalised coordinate by
	 * {@code declared / actual} on whichever axis differs. That works because the old model's UV is
	 * a pixel position divided by the size it declared, and the position it should land on is the
	 * same pixel divided by the image's real size. Only one axis is corrected: if neither matches,
	 * that is not a mistyped dimension but a different texture, and touching the UVs would make it
	 * worse. Both rules are the 1.20.1 lineage's ({@code ModelTextureFitter}).
	 */
	public static BakedRig bake(ModelGeometry.Model model, float modelScale, float uvScaleU, float uvScaleV)
	{
		BakedRig rig = new BakedRig();
		// renderScale is the GL scale a model class applied at draw time, which 1.7.10 therefore
		// never baked into the vertices; folding it in here is what makes the drawn size equal.
		float unit = modelScale * model.renderScale / 16F;

		rig.thirdPersonOffsetX = model.thirdPersonOffsetX;
		rig.thirdPersonOffsetY = model.thirdPersonOffsetY;
		rig.thirdPersonOffsetZ = model.thirdPersonOffsetZ;
		rig.itemFrameOffsetX = model.itemFrameOffsetX;
		rig.itemFrameOffsetY = model.itemFrameOffsetY;
		rig.itemFrameOffsetZ = model.itemFrameOffsetZ;
		rig.stanceRotateX = model.stanceRotateX;
		rig.stanceRotateY = model.stanceRotateY;
		rig.stanceRotateZ = model.stanceRotateZ;
		rig.stanceTranslateX = model.stanceTranslateX;
		rig.stanceTranslateY = model.stanceTranslateY;
		rig.stanceTranslateZ = model.stanceTranslateZ;
		rig.fancyStance = model.fancyStance;
		rig.animation = model;

		for(ModelGeometry.Part part : model.parts.values())
		{
			float sheetW = Math.max(1, part.sheetWidth);
			float sheetH = Math.max(1, part.sheetHeight);
			boolean partRotated = part.rotationAngleX != 0F || part.rotationAngleY != 0F || part.rotationAngleZ != 0F;

			for(ModelGeometry.Box box : part.boxes)
			{
				float boxSheetW = box.hasTexture ? Math.max(1, box.sheetWidth) : sheetW;
				float boxSheetH = box.hasTexture ? Math.max(1, box.sheetHeight) : sheetH;
				float[][] corners = corners(box);

				// Into the port's vertex order. Both the positions and the UVs are indexed by it.
				float[][] bit = new float[8][3];
				for(int i = 0; i < 8; i++)
					bit[M2BIT[i]] = corners[i];

				// flipAll() turned the geometry around on Y and Z before the rotation point was
				// applied, so the mirror belongs on the raw vertex, ahead of the pose. It is a
				// 180° turn about X (two axes negated, determinant +1), so the winding is
				// unaffected and no triangle-order fix is needed.
				if(model.flipped)
					for(int i = 0; i < 8; i++)
					{
						bit[i][1] = -bit[i][1];
						bit[i][2] = -bit[i][2];
					}

				// The element's own pose. 1.7.10's ModelRendererTurbo translated by the rotation
				// point in BOTH of its branches and only rotated when an angle was non-zero, so a
				// box with a pivot and no angles is still drawn offset by that pivot. Applying
				// this unconditionally is what keeps a gun's boxes in their own places instead of
				// stacking them all on one point.
				for(int i = 0; i < 8; i++)
					bit[i] = pose(bit[i], box.hasPivot ? box.pivotX : 0F, box.hasPivot ? box.pivotY : 0F,
							box.hasPivot ? box.pivotZ : 0F, box.rotationX, box.rotationY, box.rotationZ);

				// The part's own rotateAngle, when the class set one. It belongs to the field
				// rather than a single renderer, so it is applied about that same pivot.
				if(partRotated)
					for(int i = 0; i < 8; i++)
						bit[i] = pose(bit[i], 0F, 0F, 0F,
								part.rotationAngleX, part.rotationAngleY, part.rotationAngleZ);

				for(int poly = 0; poly < 6; poly++)
				{
					float[] rc = uvRect(poly, part, box);
					// Pixel rect to the four quad corners, in the order POLY_M lists its vertices.
					float[][] quadUv = {
							{ rc[2], rc[1] }, { rc[0], rc[1] }, { rc[0], rc[3] }, { rc[2], rc[3] },
					};
					float[][] uvAtBit = new float[8][];
					for(int i = 0; i < 4; i++)
						uvAtBit[M2BIT[POLY_M[poly][i]]] = quadUv[i];

					int[] slots = FACE_SLOTS.get(POLY_DIR[poly]);
					float[][] positions = new float[4][];
					float[][] uvs = new float[4][];
					for(int i = 0; i < 4; i++)
					{
						int slot = slots[i];
						positions[i] = new float[] {
								bit[slot][0] * unit, bit[slot][1] * unit, bit[slot][2] * unit };
						float[] uv = uvAtBit[slot];
						uvs[i] = new float[] { uv[0] / boxSheetW * uvScaleU, uv[1] / boxSheetH * uvScaleV };
					}
					float[] n = POLY_NORMAL[poly];
					Quad quad = new Quad(positions, uvs, n[0], n[1], n[2]);
					rig.quads.add(quad);
					// Filed by part as well, so the renderer can move one part on its own.
					rig.quadsByPart.computeIfAbsent(part.name, k -> new ArrayList<>()).add(quad);
					if(box.renderer!=null)rig.quadsByRenderer.computeIfAbsent(box.renderer,k->new ArrayList<>()).add(quad);
				}

				rig.expand(bit, unit);
			}

			// Prisms (addShape3D) are extruded polygons; they need a triangulation the box path
			// does not, so they are counted and reported rather than silently dropped.
			rig.skippedPrisms += part.prisms.size();
		}

		return rig;
	}

	/** Grows the bounding box to cover these corners. */
	private void expand(float[][] bit, float unit)
	{
		for(float[] v : bit)
		{
			float x = v[0] * unit, y = v[1] * unit, z = v[2] * unit;
			minX = Math.min(minX, x); maxX = Math.max(maxX, x);
			minY = Math.min(minY, y); maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
		}
	}

	/**
	 * The eight corners of a box, in the old renderer's M order, with the shape-box offsets and
	 * the expansion applied. Carried over exactly from the offline converter, which matched the
	 * 1.7.10 renderer on all 193,587 boxes of the reference corpus.
	 */
	static float[][] corners(ModelGeometry.Box b)
	{
		float f4 = b.x + b.w, f5 = b.y + b.h, f6 = b.z + b.d;
		float x = b.x - b.scale, y = b.y - b.scale, z = b.z - b.scale;
		f4 += b.scale;
		f5 += b.scale;
		f6 += b.scale;
		float[] o = b.offsets;
		return new float[][] {
				{ x - o[0], y - o[1], z - o[2] },
				{ f4 + o[3], y - o[4], z - o[5] },
				{ f4 + o[15], f5 + o[16], z - o[17] },
				{ x - o[12], f5 + o[13], z - o[14] },
				{ x - o[9], y - o[10], f6 + o[11] },
				{ f4 + o[6], y - o[7], f6 + o[8] },
				{ f4 + o[18], f5 + o[19], f6 + o[20] },
				{ x - o[21], f5 + o[22], f6 + o[23] },
		};
	}

	/**
	 * The pixel rectangle each of the six polys samples, derived from the part's texture offset
	 * and the box size — the classic Minecraft box-unwrapping layout.
	 */
	static float[] uvRect(int poly, ModelGeometry.Part part, ModelGeometry.Box b)
	{
		float d = b.d, w = b.w, h = b.h;
		float ox = b.hasTexture ? b.textureOffsetX : part.textureOffsetX;
		float oy = b.hasTexture ? b.textureOffsetY : part.textureOffsetY;
		return switch(poly)
		{
			case 0 -> new float[] { ox + d + w, oy + d, ox + d + w + d, oy + d + h };
			case 1 -> new float[] { ox, oy + d, ox + d, oy + d + h };
			case 2 -> new float[] { ox + d, oy, ox + d + w, oy + d };
			case 3 -> new float[] { ox + d + w, oy, ox + d + w + w, oy + d };
			case 4 -> new float[] { ox + d, oy + d, ox + d + w, oy + d + h };
			default -> new float[] { ox + d + w + d, oy + d, ox + d + w + d + w, oy + d + h };
		};
	}

	/**
	 * Applies a pose the way the old renderer did: translate to the pivot, rotate about it in the
	 * order Y, Z, X, and translate back. This is the shape of {@code ModelRendererTurbo.render},
	 * where the three rotations are nested between the two translations, so the pivot is both the
	 * point rotated about and the offset the geometry is placed at.
	 */
	private static float[] pose(float[] v, float px, float py, float pz,
								float angleX, float angleY, float angleZ)
	{
		double rx = Math.toRadians(angleX);
		double ry = Math.toRadians(angleY);
		double rz = Math.toRadians(angleZ);

		double cx = Math.cos(rx), sx = Math.sin(rx);
		double cy = Math.cos(ry), sy = Math.sin(ry);
		double cz = Math.cos(rz), sz = Math.sin(rz);

		double x = v[0];
		double y = v[1];
		double z = v[2];
		// X
		double y1 = y * cx - z * sx;
		double z1 = y * sx + z * cx;
		// Z
		double x2 = x * cz - y1 * sz;
		double y2 = x * sz + y1 * cz;
		// Y
		double x3 = x2 * cy + z1 * sy;
		double z3 = -x2 * sy + z1 * cy;

		return new float[] {
				(float) x3 + px,
				(float) y2 + py,
				(float) z3 + pz };
	}
}
