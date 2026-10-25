import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.AttachmentType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Prints the reflex-sight (OKP-7) reticle model layout: arrays, bounds and reticle points. */
public final class InspectReticle
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
		for(var name : new String[] { "OKP7", "OKP7Pic", "PSO1", "KobraEKPrefiexSight", "CobraEKPrefiexSight" })
		{
			var at = AttachmentType.getAttachment(name);
			if(at == null) { System.out.println(name + " missing"); continue; }
			System.out.println("=== " + name + " itsType=" + at.itsType + " attachmentType=" + at.attachmentType
					+ " scopeGlass=" + at.scopeGlassModel + " blackMask=" + at.blackMaskModel
					+ " lightStuff=" + at.lightStuffModel + " dotFrames=" + at.lightTextureCount
					+ " fovZoom=" + java.util.Arrays.toString(at.fovZoomLevels));
			var rig = ModelCache.rigFor(at, at.modelScale);
			if(rig != null && rig.animation != null)
			{
				var m = rig.animation;
				for(var key : new String[] { "reticleBorder", "reticleAt" })
				{
					var array = m.fmumVectorArrays.get(key);
					System.out.println("  " + key + "=" + (array == null ? "null" : array.length));
					if(array != null) for(var v : array) System.out.println("    " + java.util.Arrays.toString(v));
				}
				System.out.println("  normalVecOfDotBorder=" + java.util.Arrays.toString(m.fmumPose.get("normalVecOfDotBorder"))
						+ " reticleScale=" + java.util.Arrays.toString(m.fmumPose.get("reticleScale")));
			}
			var dot = at.lightStuffModel == null ? null : ModelCache.rigForReference(at.lightStuffModel, 1F);
			if(dot != null)
			{
				System.out.println("  dot quads=" + dot.quads.size()
						+ " arrays=" + dot.quadsByRenderer.keySet()
						+ " bounds=(" + dot.minX + "," + dot.minY + "," + dot.minZ + ")..("
						+ dot.maxX + "," + dot.maxY + "," + dot.maxZ + ")");
				for(var entry : dot.quadsByRenderer.entrySet())
				{
					float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
					for(var q : entry.getValue()) for(var p : q.positions())
					{
						minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);minZ=Math.min(minZ,p[2]);
						maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);maxZ=Math.max(maxZ,p[2]);
					}
					System.out.printf("    %s quads=%d bounds=(%.2f,%.2f,%.2f)..(%.2f,%.2f,%.2f)%n",
							entry.getKey(), entry.getValue().size(), minX, minY, minZ, maxX, maxY, maxZ);
				}
			}
		}
	}
}
