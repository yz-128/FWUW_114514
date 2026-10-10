package com.flansmod.client.model.loader;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.flansmod.common.TextDecoding;
import com.flansmod.common.types.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Offline, whole-assembly preview: bakes a gun and its default attachments with the same
 * transform chain the renderer uses, then rasterises the result to a PNG.
 *
 * <p>This exists because the assembled weapon — the receiver with its barrel, handguard, stock,
 * dust cover and sights — is what a player actually sees, and there is no way to tell from the
 * model files alone whether the parts landed on the right slots. Running it costs a second;
 * launching the game costs minutes.
 *
 * <p>The chain per node is FMUM's: {@code T(slot.attachPos + node.pos) · R_x(slot.attachRot)},
 * with children positioned by their own parent's slots.
 *
 * <p>Run: {@code java ... RenderAssembly <packDir> <GunShortName> <out.png>}
 */
public final class RenderAssembly
{
	private static final int W = 1280, H = 540;

	private record Part(Matrix4f matrix, BakedRig rig, BufferedImage texture) { }

	public static void main(String[] args) throws Exception
	{
		Path root = Paths.get(args[0]);
		String wanted = args[1];
		File out = new File(args[2]);

		// The model loader finds classes through the same pack list the game fills. Offline that
		// list is empty, so it is seeded here with the pack being previewed.
		com.flansmod.common.FlansMod.contentPacks.put(root.getFileName().toString(), root);

		// The same discovery rule the loader uses.
		for(EnumType type : EnumType.values())
		{
			Path dir = root.resolve(type.folderName);
			if(!Files.isDirectory(dir))
				continue;
			for(Path file : Files.newDirectoryStream(dir))
			{
				String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
				if(!Files.isRegularFile(file) || (!n.endsWith(".txt") && !n.endsWith(".df")))
					continue;
				TypeFile tf = new TypeFile(type, n.substring(0, n.lastIndexOf('.')));
				tf.lines.addAll(TextDecoding.readLines(file));
				tf.reset();
			}
		}

		GunType gun = null;
		for(ArrayList<TypeFile> list : TypeFile.files.values())
		{
			for(TypeFile tf : list)
			{
				InfoType t = tf.type.getTypeClass().getConstructor(TypeFile.class).newInstance(tf);
				tf.reset();
				t.read(tf);
				if(t instanceof GunType g && g.shortName != null && g.shortName.equalsIgnoreCase(wanted))
					gun = g;
			}
		}
		if(gun == null)
		{
			System.out.println("gun not found: " + wanted);
			return;
		}

		List<Part> parts = new ArrayList<>();
		int missing = 0;
		BakedRig body = ModelCache.rigFor(gun, gun.modelScale);
		if(body == null)
		{
			System.out.println("gun model did not bake: " + gun.modelString);
			return;
		}
		parts.add(new Part(new Matrix4f(), body, skin(root, gun.texture)));

		for(List<ModifiableType.DefaultAttachment> group : gun.defaultAttachments)
			missing += addNodes(parts, root, gun, group, new Matrix4f(), 0);

		// The arms, drawn with the same transforms the renderer uses. Offline there is no player
		// skin, so a pack arm texture is used when named; otherwise the arm is drawn untextured
		// grey so its placement can still be judged.
		int armParts = 0;
		armParts += addArm(parts, root, body, gun.leftArmModel, gun.leftArmTexture,
				"leftArmPos", "leftArmRot", "leftArmScale");
		armParts += addArm(parts, root, body, gun.rightArmModel, gun.rightArmTexture,
				"rightArmPos", "rightArmRot", "rightArmScale");

		System.out.println("gun=" + gun.shortName + " scale=" + gun.modelScale
				+ " bodyQuads=" + body.quads.size() + " parts=" + (parts.size() - 1 - armParts)
				+ " arms=" + armParts + " unbaked=" + missing);

		render(parts, out, body);
	}

	/** Bakes one arm and appends it with its declared pose; returns 1 when it drew. */
	private static int addArm(List<Part> parts, Path root, BakedRig gunRig, String modelRef, String texture,
							  String posKey, String rotKey, String scaleKey)
	{
		if(modelRef == null || modelRef.isBlank() || modelRef.equalsIgnoreCase("None"))
			return 0;
		BakedRig armRig = ModelCache.rigForReference(modelRef, 1F);
		if(armRig == null || armRig.quads.isEmpty())
			return 0;

		ModelGeometry.Model model = gunRig.animation;
		float[] pos = model == null ? null : model.fmumPose.get(posKey);
		float[] rot = model == null ? null : model.fmumPose.get(rotKey);
		float[] scale = model == null ? null : model.fmumPose.get(scaleKey);

		Matrix4f m = new Matrix4f();
		if(pos != null)
			m.translate(pos[0], pos[1], pos[2]);
		if(rot != null)
		{
			m.rotate((float)Math.toRadians(rot[0]), 1F, 0F, 0F);
			m.rotate((float)Math.toRadians(rot[1]), 0F, 1F, 0F);
			m.rotate((float)Math.toRadians(rot[2]), 0F, 0F, 1F);
		}
		if(scale != null)
			m.scale(scale[0], scale[1], scale[2]);

		BufferedImage tex = null;
		if(texture != null && !texture.isBlank() && !texture.equalsIgnoreCase("None"))
			tex = skin(root, texture);
		// Deliberately left untextured (grey) when the pack names no arm skin: the point of the
		// preview is to see WHERE the arm lands, and a black arm on a black rifle shows nothing.
		System.out.printf("  arm %s pos=%s rot=%s scale=%s%n", modelRef,
				pos == null ? "-" : String.format("%.3f %.3f %.3f", pos[0], pos[1], pos[2]),
				rot == null ? "-" : String.format("%.1f %.1f %.1f", rot[0], rot[1], rot[2]),
				scale == null ? "-" : String.format("%.2f %.2f %.2f", scale[0], scale[1], scale[2]));
		// Report where it lands, so the numbers can be compared with the gun's own bounds.
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for(BakedRig.Quad q : armRig.quads)
			for(float[] v : q.positions())
			{
				Vector3f t = m.transformPosition(new Vector3f(v[0], v[1], v[2]));
				minX = Math.min(minX, t.x); maxX = Math.max(maxX, t.x);
				minY = Math.min(minY, t.y); maxY = Math.max(maxY, t.y);
			}
		System.out.printf("     lands at x[%.3f..%.3f] y[%.3f..%.3f]%n", minX, maxX, minY, maxY);
		parts.add(new Part(m, armRig, tex));
		return 1;
	}

	/** Walks one slot's nodes, appending each part and its children. Returns how many failed to bake. */
	private static int addNodes(List<Part> parts, Path root, ModifiableType owner,
								List<ModifiableType.DefaultAttachment> nodes, Matrix4f parent, int depth)
	{
		int failed = 0;
		if(depth > 8)
			return 0;
		for(ModifiableType.DefaultAttachment node : nodes)
		{
			AttachmentType at = AttachmentType.getAttachment(node.name);
			if(at == null)
				continue;

			Matrix4f m = new Matrix4f(parent);
			if(node.slot >= 0 && node.slot < owner.slots.length)
			{
				ModifiableType.AttachableSlot slot = owner.slots[node.slot];
				m.translate(slot.x + node.pos, slot.y, slot.z);
				if(slot.rotate != 0F)
					m.rotate((float)Math.toRadians(slot.rotate), 1F, 0F, 0F);
			}

			BakedRig rig = ModelCache.rigFor(at, at.modelScale);
			if(rig == null)
			{
				failed++;
			}
			else
			{
				parts.add(new Part(new Matrix4f(m), rig, skin(root, at.texture)));
			}
			failed += addNodes(parts, root, at, node.children, m, depth + 1);
		}
		return failed;
	}

	/** Finds a skin PNG anywhere under the pack's assets/flansmod/skins, case-insensitively. */
	private static BufferedImage skin(Path packRoot, String name)
	{
		if(name == null || name.isBlank())
			return null;
		String wanted = (name.toLowerCase(Locale.ROOT).endsWith(".png")
				? name.substring(0, name.length() - 4) : name).toLowerCase(Locale.ROOT);
		Path skins = packRoot.resolve("assets/flansmod/skins");
		if(!Files.isDirectory(skins))
			return null;
		try(var walk = Files.walk(skins))
		{
			for(Path p : walk.toList())
			{
				if(!p.toString().toLowerCase(Locale.ROOT).endsWith(".png"))
					continue;
				String rel = skins.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
				if(rel.equals(wanted + ".png") || rel.endsWith("/" + wanted + ".png"))
					return ImageIO.read(p.toFile());
			}
		}
		catch(Exception ignored) { }
		return null;
	}

	// ---------------------------------------------------------------- rasteriser

	private static void render(List<Part> parts, File out, BakedRig body) throws Exception
	{
		// Frame the whole assembly: its bounds decide the camera distance.
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for(Part p : parts)
		{
			for(BakedRig.Quad q : p.rig.quads)
			{
				for(float[] v : q.positions())
				{
					Vector3f t = p.matrix.transformPosition(new Vector3f(v[0], v[1], v[2]));
					minX = Math.min(minX, t.x); maxX = Math.max(maxX, t.x);
					minY = Math.min(minY, t.y); maxY = Math.max(maxY, t.y);
				}
			}
		}
		float cx = (minX + maxX) * 0.5F, cy = (minY + maxY) * 0.5F;
		float span = Math.max(maxX - minX, (maxY - minY) * 1.6F) + 0.1F;
		float dist = span / (2F * (float)Math.tan(Math.toRadians(35F))) * 1.15F;
		System.out.printf("bounds x[%.3f..%.3f] y[%.3f..%.3f] span=%.3f dist=%.3f%n",
				minX, maxX, minY, maxY, span, dist);

		// View: straight side view, camera on +Z looking at the assembly's centre.
		Matrix4f view = new Matrix4f().translate(-cx, -cy, -dist);

		float[] zbuf = new float[W * H];
		Arrays.fill(zbuf, Float.POSITIVE_INFINITY);
		int[] pix = new int[W * H];
		Arrays.fill(pix, 0xFF8899AA);

		float tanHalf = (float)Math.tan(Math.toRadians(35F));
		float aspect = (float)W / H;

		int drawn = 0;
		for(Part part : parts)
		{
			for(BakedRig.Quad quad : part.rig.quads)
			{
				Vector3f[] v = new Vector3f[4];
				boolean behind = false;
				for(int i = 0; i < 4; i++)
				{
					float[] p = quad.positions()[i];
					Vector3f world = part.matrix.transformPosition(new Vector3f(p[0], p[1], p[2]));
					v[i] = view.transformPosition(world);
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
				float shade = 0.35F + 0.65F * lambert;

				float[][] uv = quad.uvs();
				for(int tri = 0; tri < 2; tri++)
				{
					int a = 0, b = tri == 0 ? 1 : 2, c = tri == 0 ? 2 : 3;
					raster(v[a], v[b], v[c], uv[a], uv[b], uv[c], shade, part.texture, zbuf, pix, tanHalf, aspect);
				}
				drawn++;
			}
		}

		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, W, H, pix, 0, W);
		ImageIO.write(img, "png", out);
		System.out.println("wrote " + out + "  quads drawn=" + drawn);
	}

	private static void raster(Vector3f p0, Vector3f p1, Vector3f p2, float[] uv0, float[] uv1, float[] uv2,
							   float shade, BufferedImage tex, float[] zbuf, int[] pix, float tanHalf, float aspect)
	{
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
				float depth = w0 * s[0][2] + w1 * s[1][2] + w2 * s[2][2];
				int idx = y * W + x;
				if(depth < 0F || depth > 40F || depth >= zbuf[idx])
					continue;

				int c;
				if(tex == null)
				{
					// No skin could be found: a mid grey so the silhouette still reads.
					c = 0xFF808080;
				}
				else
				{
					float u = (w0 * uv[0][0] * iw0 + w1 * uv[1][0] * iw1 + w2 * uv[2][0] * iw2) / iw;
					float v = (w0 * uv[0][1] * iw0 + w1 * uv[1][1] * iw1 + w2 * uv[2][1] * iw2) / iw;
					int tx = Math.min(tex.getWidth() - 1, Math.max(0, (int)(u * tex.getWidth())));
					int ty = Math.min(tex.getHeight() - 1, Math.max(0, (int)(v * tex.getHeight())));
					c = tex.getRGB(tx, ty);

					// Alpha-test, exactly as the game's entityCutout does. A pack's skins are
					// mostly transparent (the EFT rifle atlas is ~75% empty), and a texel like
					// that draws NOTHING — not black. Ignoring this painted every transparent UV
					// island as solid dark, which is what made the offline previews look like
					// all-black rifles while the game shows a normal textured gun.
					if(((c >>> 24) & 0xFF) < 26)
						continue;
				}
				// The depth buffer is only written for texels that actually drew, so a nearer
				// transparent island cannot hide a farther opaque surface behind it.
				zbuf[idx] = depth;

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
}
