package com.flansmod.client.model.loader;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Textured software renderer for one baked model through the 1.7.10 first-person chain.
 *
 * <p>Purpose: decide visually, offline, whether a renderer's on-screen gun is mirrored relative
 * to the reference. The texture makes the gun's sides distinguishable (the StG-44's charging
 * handle, magazine curve and wooden furniture are all asymmetric), which a flat silhouette
 * cannot show.
 *
 * <p>Run: {@code java ... RenderPreviewTex <pack> <Model.class> <texture.png> <out.png> [mirrorX|ry180|ry135]}
 */
public final class RenderPreviewTex
{
	private static final int W = 960, H = 720;
	private static final float FOV = 70F;

	public static void main(String[] args) throws Exception
	{
		Path input = Paths.get(args[0]);
		String modelName = args[1];
		BufferedImage tex = ImageIO.read(new File(args[2]));
		File out = new File(args[3]);
		String mode = args.length > 4 ? args[4] : "none";

		ModelGeometry.Model model = BytecodeModel.fromStream(new java.io.ByteArrayInputStream(read(input, modelName)));
		BakedRig rig = BakedRig.bake(model, mode.startsWith("fmu2") ? 0.25F : 1F);

		Matrix4f chain = chain(mode);

		// z-buffer rasterisation in view space
		float[] zbuf = new float[W * H];
		java.util.Arrays.fill(zbuf, Float.POSITIVE_INFINITY);
		int[] pix = new int[W * H];
		java.util.Arrays.fill(pix, 0xFF6E96BE);

		float tanHalf = (float)Math.tan(Math.toRadians(FOV * 0.5));
		float aspect = (float)W / H;

		for(BakedRig.Quad quad : rig.quads)
		{
			Vector3f[] v = new Vector3f[4];
			float[][] uv = quad.uvs();
			boolean behind = false;
			for(int i = 0; i < 4; i++)
			{
				float[] p = quad.positions()[i];
				float x = p[0], y = p[1], z = p[2];
				if("mirrorX".equals(mode))
					x = -x;
				v[i] = chain.transformPosition(new Vector3f(x, y, z));
				if(v[i].z > -0.02F)
					behind = true;
			}
			if(behind)
				continue;

			Vector3f e1 = new Vector3f(v[1]).sub(v[0]);
			Vector3f e2 = new Vector3f(v[3]).sub(v[0]);
			Vector3f n = e1.cross(e2).normalize();
			Vector3f mid = new Vector3f(v[0]).add(v[1]).add(v[2]).add(v[3]).mul(0.25F);
			float lambert = Math.abs(n.dot(new Vector3f(mid).negate().normalize()));
			float shade = 0.30F + 0.70F * lambert;

			for(int tri = 0; tri < 2; tri++)
			{
				int a = 0, b = tri == 0 ? 1 : 2, c = tri == 0 ? 2 : 3;
				raster(v[a], v[b], v[c], uv[a], uv[b], uv[c], shade, tex, zbuf, pix, tanHalf, aspect);
			}
		}

		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, W, H, pix, 0, W);
		ImageIO.write(img, "png", out);
		System.out.println("wrote " + out + " mode=" + mode);
	}

	private static void raster(Vector3f p0, Vector3f p1, Vector3f p2,
							   float[] uv0, float[] uv1, float[] uv2, float shade,
							   BufferedImage tex, float[] zbuf, int[] pix, float tanHalf, float aspect)
	{
		// project to screen
		float[][] s = new float[3][3];
		Vector3f[] p = { p0, p1, p2 };
		float[][] uv = { uv0, uv1, uv2 };
		for(int i = 0; i < 3; i++)
		{
			float ndcX = p[i].x / (-p[i].z * tanHalf * aspect);
			float ndcY = p[i].y / (-p[i].z * tanHalf);
			s[i][0] = (ndcX + 1F) * 0.5F * W;
			s[i][1] = (1F - (ndcY + 1F) * 0.5F) * H;
			s[i][2] = -p[i].z;
		}

		int minX = (int)Math.max(0, Math.floor(Math.min(s[0][0], Math.min(s[1][0], s[2][0]))));
		int maxX = (int)Math.min(W - 1, Math.ceil(Math.max(s[0][0], Math.max(s[1][0], s[2][0]))));
		int minY = (int)Math.max(0, Math.floor(Math.min(s[0][1], Math.min(s[1][1], s[2][1]))));
		int maxY = (int)Math.min(H - 1, Math.ceil(Math.max(s[0][1], Math.max(s[1][1], s[2][1]))));
		if(maxX < minX || maxY < minY)
			return;

		float area = edge(s[0], s[1], s[2]);
		if(Math.abs(area) < 1e-6F)
			return;

		float iw0 = 1F / s[0][2], iw1 = 1F / s[1][2], iw2 = 1F / s[2][2];

		for(int y = minY; y <= maxY; y++)
		{
			for(int x = minX; x <= maxX; x++)
			{
				float px = x + 0.5F, py = y + 0.5F;
				float w0 = edge(s[1], s[2], px, py) / area;
				float w1 = edge(s[2], s[0], px, py) / area;
				float w2 = edge(s[0], s[1], px, py) / area;
				if(w0 < 0F || w1 < 0F || w2 < 0F)
					continue;

				float iw = w0 * iw0 + w1 * iw1 + w2 * iw2;
				float depth = (w0 * s[0][2] + w1 * s[1][2] + w2 * s[2][2]);
				// use interpolated view depth directly (linear in view space is close enough here)
				int idx = y * W + x;
				if(depth < 0F || depth > 20F)
					continue;
				if(depth >= zbuf[idx])
					continue;
				zbuf[idx] = depth;

				float u = (w0 * uv[0][0] * iw0 + w1 * uv[1][0] * iw1 + w2 * uv[2][0] * iw2) / iw;
				float v = (w0 * uv[0][1] * iw0 + w1 * uv[1][1] * iw1 + w2 * uv[2][1] * iw2) / iw;
				int tx = (int)(u * tex.getWidth()) & (tex.getWidth() - 1);
				int ty = (int)(v * tex.getHeight()) & (tex.getHeight() - 1);
				if(tx < 0) tx += tex.getWidth();
				if(ty < 0) ty += tex.getHeight();
				int c = tex.getRGB(tx, ty);
				int r = (int)Math.min(255, ((c >> 16) & 0xFF) * shade);
				int g = (int)Math.min(255, ((c >> 8) & 0xFF) * shade);
				int b = (int)Math.min(255, (c & 0xFF) * shade);
				pix[idx] = (r << 16) | (g << 8) | b;
			}
		}
	}

	private static float edge(float[] a, float[] b, float px, float py)
	{
		return (px - a[0]) * (b[1] - a[1]) - (py - a[1]) * (b[0] - a[0]);
	}

	private static float edge(float[] a, float[] b, float[] c)
	{
		return edge(a, b, c[0], c[1]);
	}

	private static Matrix4f chain(String mode)
	{
		Matrix4f m = new Matrix4f();
		if("side".equals(mode))
		{
			// Plain side view: camera at the origin looking down -Z, model pushed in front.
			// Model +X points screen right. The gun bakes to roughly 2.5 x 0.9 units, the
			// distance puts it comfortably inside the 70 degrees frustum.
			m.translate(0F, 0F, -3.2F);
			return m;
		}
		m.translate(0.56F, -0.52F, -0.72F);

		// "fmu2[:ads]": the whole first-person chain of Flan's Mod Ultimate 2.0 for 1.20.1, read off
		// its GunItemRenderer (applyFirstPersonAdjustments). Vanilla's arm transform above, then the
		// half-block shift modern ItemRenderer.render adds, then the mod's own rotation and offset —
		// the edited build keeps that shift rather than undoing it. Used to check a candidate pose
		// against a 1.20.1 reference screenshot.
		if(mode.startsWith("fmu2"))
		{
			// Flan's Mod Ultimate 2.0's first-person pose, with nothing else: its BEWLR undoes the
			// half-block shift (like this mod's renderer), the guns carry no display transform, and
			// the adjustments are Ry(90) plus one offset — no 45-degree arm turn, no 0.4 scale and
			// no 25-degree roll, all three of which belong to the 1.7.10 renderer FMU replaced.
			float ads = mode.contains(":") ? Float.parseFloat(mode.substring(mode.indexOf(':') + 1)) : 0F;
			m.rotate((float)Math.toRadians(90F), 0F, 1F, 0F);
			m.rotate((float)Math.toRadians(-5F * ads), 0F, 0F, 1F);
			m.translate(-0.25F, -0.05F + 0.175F * ads, -0.155F - 0.405F * ads);
			m.rotate((float)Math.toRadians(4.5F * ads), 0F, 0F, 1F);
			m.translate(0F, -0.03F * ads, 0F);
			return m;
		}

		// Pose-scan modes: "rot:<pitch>:<yaw>:<roll>[:mirror]" applies extra rotations at the hand
		// anchor, before the base chain, so candidates can be compared against a reference shot.
		if(mode.startsWith("rot:") || mode.startsWith("mirror:") || mode.startsWith("rotm:"))
		{
			boolean mirror = mode.startsWith("mirror:") || mode.startsWith("rotm:");
			String body = mode.substring(mode.indexOf(':') + 1);
			String[] parts = body.split(":");
			if(mirror)
				m.scale(-1F, 1F, 1F);
			m.rotate((float)Math.toRadians(Float.parseFloat(parts[2])), 0F, 0F, 1F);
			m.rotate((float)Math.toRadians(Float.parseFloat(parts[1])), 0F, 1F, 0F);
			m.rotate((float)Math.toRadians(Float.parseFloat(parts[0])), 1F, 0F, 0F);
		}

		m.rotate((float)Math.toRadians(45F), 0F, 1F, 0F);
		m.scale(0.4F, 0.4F, 0.4F);
		if(mode.startsWith("yaw"))
		{
			// Diagnostic: replace the RenderGun yaw with an arbitrary net yaw so the on-screen
			// orientation for each candidate can be compared with a reference screenshot.
			// The base chain already contributed 45 about Y, so the extra yaw is net - 45; the
			// resting pose's Rz(25) roll and offset are kept.
			float net = Float.parseFloat(mode.substring(3));
			m.rotate((float)Math.toRadians(25F), 0F, 0F, 1F);
			m.rotate((float)Math.toRadians(net - 45F), 0F, 1F, 0F);
			m.translate(0.15F, 0.2F, -0.6F);
			return m;
		}
		if("ry180".equals(mode))
			m.rotate((float)Math.toRadians(180F), 0F, 1F, 0F);
		if("ry135".equals(mode))
			m.rotate((float)Math.toRadians(90F), 0F, 1F, 0F);

		if("ads".equals(mode))
		{
			// 1.7.10's aiming pose at adsSwitch == 1: Rz(25-5) · Ry(-5) · T(0.15, 0.375, -1.005)
			// · Rz(4.5) · T(0, -0.03, 0).
			m.rotate((float)Math.toRadians(20F), 0F, 0F, 1F);
			m.rotate((float)Math.toRadians(-5F), 0F, 1F, 0F);
			m.translate(0.15F, 0.375F, -1.005F);
			m.rotate((float)Math.toRadians(4.5F), 0F, 0F, 1F);
			m.translate(0F, -0.03F, 0F);
			return m;
		}

		if("fmum_sprint".equals(mode))
		{
			// FMUM 1.51's sprint branch, from the running jar's bytecode: the model's stanceRotate
			// is added to the resting angles and stanceTranslate to the offset. For the StG-44's
			// (25, 0, -15) / (0.4, 0, 0): Rz(25-15) · Ry(-5+25) · T(0.15,0.2,-0.6) · T(0.4,0,0).
			m.rotate((float)Math.toRadians(10F), 0F, 0F, 1F);
			m.rotate((float)Math.toRadians(20F), 0F, 1F, 0F);
			m.translate(0.15F, 0.2F, -0.6F);
			m.translate(0.4F, 0F, 0F);
			return m;
		}

		m.rotate((float)Math.toRadians(25F), 0F, 0F, 1F);
		m.rotate((float)Math.toRadians(-5F), 0F, 1F, 0F);
		m.translate(0.15F, 0.2F, -0.6F);

		if("reload_custom".equals(mode))
		{
			// 1.7.10 RenderGun's CUSTOM reload case, at reloadRotate == 1, with the StG-44's
			// declared values rotateGunVertical=35, rotateGunHorizontal=1, tiltGun=25,
			// translateGun=(0.06, 0.13, -0.06).
			m.rotate((float)Math.toRadians(35F), 0F, 0F, 1F);
			m.rotate((float)Math.toRadians(1F), 0F, 1F, 0F);
			m.rotate((float)Math.toRadians(25F), 1F, 0F, 0F);
			m.translate(0.06F, 0.13F, -0.06F);
		}
		return m;
	}

	private static byte[] read(Path input, String wanted) throws Exception
	{
		if(Files.isDirectory(input))
		{
			try(var walk = Files.walk(input))
			{
				for(Path p : walk.toList())
					if(p.getFileName().toString().equals(wanted))
						return Files.readAllBytes(p);
			}
		}
		else
		{
			try(var zip = new java.util.zip.ZipFile(input.toFile()))
			{
				for(var e : java.util.Collections.list(zip.entries()))
					if(e.getName().endsWith("/" + wanted) || e.getName().equals(wanted))
						try(var in = zip.getInputStream(e))
						{
							return in.readAllBytes();
						}
			}
		}
		throw new IllegalArgumentException("not found: " + wanted);
	}
}
