package com.flansmod.client.model.loader;

import com.flansmod.client.model.loader.ModelGeometry.Box;
import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.client.model.loader.ModelGeometry.Part;
import com.flansmod.client.model.loader.ModelGeometry.Prism;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline check of the first-person transform chain.
 *
 * <p>Bakes one model, then places its bounding box through two candidate chains and prints where
 * the muzzle (the box end that is furthest along the model's own +X, which is where every Flan gun
 * points) lands in view space, plus the projected screen position of the muzzle and stock.
 *
 * <p>Chain A is what the 1.7.10 sources say: vanilla's
 * {@code T(0.56,-0.52,-0.72) · R_y(45°) · S(0.4)} then {@code RenderGun}'s
 * {@code R_z(25°) · R_y(-5°) · T(0.15,0.2,-0.6)}.
 *
 * <p>Chain B adds the {@code R_y(180°)} that modern item transforms carry inside their model
 * display block, which is the one difference that would flip a gun from nose-right to nose-left.
 *
 * <p>Run: {@code java -cp <classes>:<asm> com.flansmod.client.model.loader.PoseCheck <pack> <ModelName>}
 */
public final class PoseCheck
{
	public static void main(String[] args) throws Exception
	{
		Path input = Paths.get(args[0]);
		String wanted = args[1];

		byte[] bytes = read(input, wanted);
		Model model = BytecodeModel.fromStream(new java.io.ByteArrayInputStream(bytes));
		BakedRig rig = BakedRig.bake(model, 1F);

		System.out.printf("model %s: x[%.2f..%.2f] y[%.2f..%.2f] z[%.2f..%.2f] flipped=%b%n",
				wanted, rig.minX, rig.maxX, rig.minY, rig.maxY, rig.minZ, rig.maxZ, model.flipped);

		// The barrel points along +X in model space for every Flan gun; the stock is the -X end.
		Vector3f muzzle = new Vector3f(rig.maxX, (rig.minY + rig.maxY) * 0.5F, (rig.minZ + rig.maxZ) * 0.5F);
		Vector3f stock = new Vector3f(rig.minX, (rig.minY + rig.maxY) * 0.5F, (rig.minZ + rig.maxZ) * 0.5F);
		Vector3f centre = new Vector3f((rig.minX + rig.maxX) * 0.5F, (rig.minY + rig.maxY) * 0.5F,
				(rig.minZ + rig.maxZ) * 0.5F);

		report("A: 1.7.10 plain", chain(false), muzzle, stock, centre);
		report("B: with Ry(180)", chain(true), muzzle, stock, centre);
	}

	private static Matrix4f chain(boolean flip)
	{
		Matrix4f m = new Matrix4f();
		// vanilla: translate then Ry(45) then S(0.4)  (applied to the item, right to left)
		m.translate(0.56F, -0.52F, -0.72F);
		m.rotate((float)Math.toRadians(45F), 0F, 1F, 0F);
		m.scale(0.4F, 0.4F, 0.4F);
		if(flip)
			m.rotate((float)Math.toRadians(180F), 0F, 1F, 0F);
		// RenderGun EQUIPPED_FIRST_PERSON resting pose
		m.rotate((float)Math.toRadians(25F), 0F, 0F, 1F);
		m.rotate((float)Math.toRadians(-5F), 0F, 1F, 0F);
		m.translate(0.15F, 0.2F, -0.6F);
		return m;
	}

	private static void report(String label, Matrix4f m, Vector3f muzzle, Vector3f stock, Vector3f centre)
	{
		Vector3f vm = m.transformPosition(new Vector3f(muzzle));
		Vector3f vs = m.transformPosition(new Vector3f(stock));
		Vector3f vc = m.transformPosition(new Vector3f(centre));
		System.out.printf("%n%s%n", label);
		System.out.printf("  muzzle view=(%.3f, %.3f, %.3f)%n", vm.x, vm.y, vm.z);
		System.out.printf("  centre view=(%.3f, %.3f, %.3f)%n", vc.x, vc.y, vc.z);
		System.out.printf("  stock  view=(%.3f, %.3f, %.3f)%n", vs.x, vs.y, vs.z);
		System.out.printf("  -> muzzle is %s of centre on screen; nose points %s%n",
				vm.x < vc.x ? "LEFT" : "RIGHT", vm.x < vc.x ? "left" : "right");
		System.out.printf("  -> muzzle depth %.3f (negative is in front of the camera)%n", vm.z);
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
					if(e.getName().endsWith("/" + wanted))
						try(var in = zip.getInputStream(e))
						{
							return in.readAllBytes();
						}
			}
		}
		throw new IllegalArgumentException("not found: " + wanted);
	}
}
