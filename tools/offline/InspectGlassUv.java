import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.AttachmentType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Prints the UV extent of the scope glass mesh (PSO1Glass / ELCAN) used for the ocular image. */
public final class InspectGlassUv
{
	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var root = Path.of(args[0]);
		for(var dir : (Iterable<Path>)Files.list(root).sorted()::iterator)
		{
			if(!Files.exists(dir.resolve("pack.mcmeta"))) continue;
			try { new OfflinePack(dir).readTypes(); } catch(RuntimeException ignored) { }
		}
		for(var name : new String[] { "SeecterDR1x4x", "PSO1" })
		{
			var at = AttachmentType.getAttachment(name);
			if(at == null) { System.out.println(name + " missing"); continue; }
			System.out.println("=== " + name + " glass=" + at.scopeGlassModel + " scale=" + at.modelScale);
			var glass = at.scopeGlassModel == null ? null : ModelCache.rigForReference(at.scopeGlassModel, 1F);
			if(glass == null) { System.out.println("  glass rig missing"); continue; }
			for(var variant : new String[] { "all", "eyeFacing" })
			{
				float minU = Float.POSITIVE_INFINITY, minV = minU, maxU = Float.NEGATIVE_INFINITY, maxV = maxU;
				int quads = 0;
				for(var quad : glass.quads)
				{
					if(variant.equals("eyeFacing") && quad.nx() >= -.9F) continue;
					quads++;
					for(var uv : quad.uvs())
					{
						minU = Math.min(minU, uv[0]); maxU = Math.max(maxU, uv[0]);
						minV = Math.min(minV, uv[1]); maxV = Math.max(maxV, uv[1]);
					}
				}
				System.out.printf("  %s quads=%d u=[%.4f,%.4f] v=[%.4f,%.4f]%n", variant, quads, minU, maxU, minV, maxV);
			}
			// per quad first four
			int shown = 0;
			for(var quad : glass.quads)
			{
				if(quad.nx() >= -.9F || shown++ >= 4) continue;
				System.out.printf("    quad nx=%.2f uv=%s%n", quad.nx(),
						java.util.Arrays.deepToString(quad.uvs()));
			}
		}
	}
}
