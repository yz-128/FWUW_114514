package com.flansmod.client.model.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bakes whole content packs and reports every model's bounding box, so a geometric regression is
 * visible offline instead of only on screen.
 *
 * <p>Run from the project root, e.g.
 * {@code java -cp <classes>:<asm-jars> com.flansmod.client.model.loader.BakeTest <pack-root> [filter]}
 *
 * <p>An archive can be passed directly; it is read through the same {@code ZipFile} path the game
 * uses. The numbers to sanity-check are the bounds: a model whose box hugs the origin has lost its
 * parts' rotation points, and one whose box is far larger or smaller than its neighbours has a
 * scale problem.
 */
public final class BakeTest
{
	public static void main(String[] args) throws Exception
	{
		Path input = Paths.get(args.length > 0 ? args[0] : "run/Flan");
		String filter = args.length > 1 && !args[1].startsWith("-")
				? args[1].toLowerCase(Locale.ROOT) : null;
		// "-a" prints the animation parameters each model declares.
		boolean verboseAnimations = false;
		for(String arg : args)
			if("-a".equals(arg))
				verboseAnimations = true;

		List<String> names = new ArrayList<>();
		List<byte[]> classBytes = new ArrayList<>();

		if(Files.isDirectory(input))
		{
			try(var walk = Files.walk(input))
			{
				for(Path p : walk.filter(x -> x.toString().endsWith(".class")).toList())
				{
					String s = p.toString().replace('\\', '/');
					if(!s.contains("com/flansmod/client/model/"))
						continue;
					names.add(p.getFileName().toString());
					classBytes.add(Files.readAllBytes(p));
				}
			}
		}
		else
		{
			try(var zip = new java.util.zip.ZipFile(input.toFile()))
			{
				for(var e : java.util.Collections.list(zip.entries()))
				{
					if(e.isDirectory() || !e.getName().endsWith(".class"))
						continue;
					if(!e.getName().replace('\\', '/').contains("com/flansmod/client/model/"))
						continue;
					names.add(e.getName().substring(e.getName().lastIndexOf('/') + 1));
					try(var in = zip.getInputStream(e))
					{
						classBytes.add(in.readAllBytes());
					}
				}
			}
		}

		int shown = 0, empty = 0, failed = 0;
		long quads = 0;
		for(int i = 0; i < names.size(); i++)
		{
			String name = names.get(i);
			if(filter != null && !name.toLowerCase(Locale.ROOT).contains(filter))
				continue;

			ModelGeometry.Model model;
			try
			{
				model = BytecodeModel.fromStream(new java.io.ByteArrayInputStream(classBytes.get(i)));
			}
			catch(Throwable t)
			{
				failed++;
				System.out.printf("FAIL  %-32s %s%n", name, t);
				continue;
			}

			BakedRig rig = BakedRig.bake(model, 1F);
			if(rig.quads.isEmpty())
			{
				empty++;
				continue;
			}
			shown++;
			quads += rig.quads.size();

			System.out.printf("%-32s %6d quads  x[%8.2f..%8.2f] y[%8.2f..%8.2f] z[%8.2f..%8.2f]  %6.2f x %6.2f x %6.2f%n",
					name, rig.quads.size(),
					rig.minX, rig.maxX, rig.minY, rig.maxY, rig.minZ, rig.maxZ,
					rig.width(), rig.height(), rig.depth());

			// The FMUM-family pose vectors are stored but never applied; list them so it is
			// visible which vocabulary a pack speaks. Printed only with -a, and before the
			// animation block below so it shows even for a model that declares no animation.
			if(verboseAnimations && !model.fmumPose.isEmpty())
				System.out.printf("      fmumPose: %d vectors (%s%s)%n",
						model.fmumPose.size(),
						String.join(", ", model.fmumPose.keySet().stream().limit(6).toList()),
						model.fmumPose.size() > 6 ? ", …" : "");

			// The animation parameters are invisible in the bounding box but decide how the gun
			// behaves, so print the ones that differ from the defaults — a model that sets none
			// stays silent.
			if(verboseAnimations && model.animationType != null && !"NONE".equals(model.animationType))
			{
				System.out.printf("      stance=(%.1f,%.1f,%.1f)%n",
						model.stanceRotateX, model.stanceRotateY, model.stanceRotateZ);
				System.out.printf("      animation=%s slide=%.3f tilt=%.2f/%.2f/%.2f/%.2f pump=%d/%d/%d%.0f tpOffset=(%.2f,%.2f,%.2f)%n",
						model.animationType, model.gunSlideDistance,
						model.tiltGunTime, model.unloadClipTime, model.loadClipTime, model.untiltGunTime,
						model.pumpDelay, model.pumpDelayAfterReload, model.pumpTime, model.pumpHandleDistance,
						model.thirdPersonOffsetX, model.thirdPersonOffsetY, model.thirdPersonOffsetZ);
				if(model.rotateGunVertical != 0 || model.rotateGunHorizontal != 0 || model.tiltGun != 0
						|| model.translateGun[0] != 0 || model.translateGun[1] != 0 || model.translateGun[2] != 0)
					System.out.printf("      gunPose rotate=(%.1f,%.1f,%.1f) translate=(%.2f,%.2f,%.2f)%n",
							model.rotateGunVertical, model.rotateGunHorizontal, model.tiltGun,
							model.translateGun[0], model.translateGun[1], model.translateGun[2]);
				if(model.rotateClipVertical != 0 || model.rotateClipHorizontal != 0 || model.tiltClip != 0
						|| model.translateClip[0] != 0 || model.translateClip[1] != 0 || model.translateClip[2] != 0)
					System.out.printf("      clipPose rotate=(%.1f,%.1f,%.1f) translate=(%.2f,%.2f,%.2f)%n",
							model.rotateClipVertical, model.rotateClipHorizontal, model.tiltClip,
							model.translateClip[0], model.translateClip[1], model.translateClip[2]);
			}
		}

		System.out.printf("%n%d shown, %d empty, %d failed, %d quads total%n",
				shown, empty, failed, quads);
	}
}
