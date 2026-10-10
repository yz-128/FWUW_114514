package com.flansmod.client.model.loader;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Software rasteriser for one baked model seen through the first-person chain, so the on-screen
 * orientation can be checked without launching the game.
 *
 * <p>Draws the quads with a painter's algorithm and a headlight lambert term — enough to tell a
 * muzzle from a stock and a left-facing gun from a right-facing one.
 *
 * <p>Run: {@code java ... com.flansmod.client.model.loader.RenderPreview <pack> <Model.class> <out.png> [flip]}
 */
public final class RenderPreview
{
	private static final int W = 1280, H = 720;
	private static final float FOV = 70F;

	public static void main(String[] args) throws Exception
	{
		Path input = Paths.get(args[0]);
		String modelName = args[1];
		File out = new File(args[2]);
		boolean flip = args.length > 3 && "flip".equals(args[3]);

		ModelGeometry.Model model = BytecodeModel.fromStream(new java.io.ByteArrayInputStream(read(input, modelName)));
		BakedRig rig = BakedRig.bake(model, 1F);

		Matrix4f chain = chain(flip);

		List<Draw> draws = new ArrayList<>();
		for(BakedRig.Quad quad : rig.quads)
		{
			Vector3f[] v = new Vector3f[4];
			boolean ok = true;
			for(int i = 0; i < 4; i++)
			{
				float[] p = quad.positions()[i];
				v[i] = chain.transformPosition(new Vector3f(p[0], p[1], p[2]));
				if(v[i].z > -0.05F)
					ok = false;
			}
			if(!ok)
				continue;

			// Face normal from the transformed corners; the light is at the camera, so a quad
			// that faces away is just as dark as one that faces the camera — take the absolute.
			Vector3f e1 = new Vector3f(v[1]).sub(v[0]);
			Vector3f e2 = new Vector3f(v[3]).sub(v[0]);
			Vector3f n = e1.cross(e2).normalize();
			Vector3f mid = new Vector3f();
			for(Vector3f p : v)
				mid.add(p);
			mid.mul(0.25F);

			float lambert = Math.abs(n.dot(new Vector3f(mid).negate().normalize()));
			float shade = 0.22F + 0.78F * lambert;
			draws.add(new Draw(v, shade, mid.z));
		}

		draws.sort((a, b) -> Float.compare(a.depth, b.depth));

		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(110, 150, 190));
		g.fillRect(0, 0, W, H);

		for(Draw d : draws)
		{
			Polygon poly = new Polygon();
			for(Vector3f p : d.v)
			{
				float tanHalf = (float)Math.tan(Math.toRadians(FOV * 0.5));
				float aspect = (float)W / H;
				float ndcX = p.x / (-p.z * tanHalf * aspect);
				float ndcY = p.y / (-p.z * tanHalf);
				int sx = Math.round((ndcX + 1F) * 0.5F * W);
				int sy = Math.round((1F - (ndcY + 1F) * 0.5F) * H);
				poly.addPoint(sx, sy);
			}
			int c = Math.max(0, Math.min(255, Math.round(d.shade * 190)));
			g.setColor(new Color(c, c, c));
			g.fillPolygon(poly);
		}

		g.setColor(Color.RED);
		g.drawLine(W / 2 - 10, H / 2, W / 2 + 10, H / 2);
		g.drawLine(W / 2, H / 2 - 10, W / 2, H / 2 + 10);
		g.dispose();
		ImageIO.write(img, "png", out);
		System.out.println("wrote " + out + " (" + draws.size() + " quads, flip=" + flip + ")");
	}

	private static Matrix4f chain(boolean flip)
	{
		Matrix4f m = new Matrix4f();
		m.translate(0.56F, -0.52F, -0.72F);
		m.rotate((float)Math.toRadians(45F), 0F, 1F, 0F);
		m.scale(0.4F, 0.4F, 0.4F);
		if(flip)
			m.rotate((float)Math.toRadians(180F), 0F, 1F, 0F);
		m.rotate((float)Math.toRadians(25F), 0F, 0F, 1F);
		m.rotate((float)Math.toRadians(-5F), 0F, 1F, 0F);
		m.translate(0.15F, 0.2F, -0.6F);
		return m;
	}

	private record Draw(Vector3f[] v, float shade, float depth) { }

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
