package com.flansmod.client.model.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Standalone check for {@link BytecodeModel}: reads real content-pack model classes and reports
 * what geometry came back.
 *
 * <p>Run from the project root, e.g.
 * {@code java -cp <classes> com.flansmod.client.model.loader.BytecodeModelTest <dir>}
 */
public final class BytecodeModelTest
{
	public static void main(String[] args) throws Exception
	{
		Path root = Paths.get(args.length > 0 ? args[0] : "run/Flan");
		boolean verbose = args.length > 1 && args[1].equals("-v");

		List<Path> classes = new ArrayList<>();
		if(Files.isDirectory(root))
		{
			try(Stream<Path> walk = Files.walk(root))
			{
				walk.filter(p -> p.toString().endsWith(".class"))
						.filter(p -> p.toString().contains("model"))
						.forEach(classes::add);
			}
		}
		else
		{
			classes.add(root);
		}

		int ok = 0, empty = 0, failed = 0;
		long boxes = 0, prisms = 0;
		List<String> failures = new ArrayList<>();

		for(Path file : classes)
		{
			try
			{
				ModelGeometry.Model model = BytecodeModel.fromFile(file);
				int n = model.elementCount();
				if(n == 0)
				{
					empty++;
					continue;
				}
				ok++;
				for(ModelGeometry.Part p : model.parts.values())
				{
					boxes += p.boxes.size();
					prisms += p.prisms.size();
				}
				if(verbose)
					System.out.printf("  %-34s %-42s %2d parts %5d elements%n",
							model.simpleName, model.superName.replace("com.flansmod.client.model.", ""),
							model.parts.size(), n);
			}
			catch(Throwable t)
			{
				failed++;
				failures.add(file.getFileName() + " : " + t);
			}
		}

		System.out.printf("%nScanned %d class(es)%n", classes.size());
		System.out.printf("  with geometry : %d%n", ok);
		System.out.printf("  no geometry   : %d%n", empty);
		System.out.printf("  failed        : %d%n", failed);
		System.out.printf("  boxes         : %d%n", boxes);
		System.out.printf("  prisms        : %d%n", prisms);
		if(!failures.isEmpty())
		{
			System.out.println("Failures:");
			failures.stream().limit(10).forEach(f -> System.out.println("  " + f));
		}
	}
}
