import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.AttachmentType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Per-array geometry/UV layout of the ELCAN reticle model (which arrays are peripheral occluders). */
public final class InspectElcanDot
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
		var at = AttachmentType.getAttachment("SeecterDR1x4x");
		System.out.println("dotModel=" + at.lightStuffModel + " dotFrames=" + at.lightTextureCount);
		var dot = ModelCache.rigForReference(at.lightStuffModel, 1F);
		if(dot == null) { System.out.println("missing"); return; }
		System.out.println("arrays=" + dot.quadsByRenderer.size() + " totalQuads=" + dot.quads.size());
		for(var entry : dot.quadsByRenderer.entrySet())
		{
			float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
			float minU=Float.POSITIVE_INFINITY,minV=minU,maxU=Float.NEGATIVE_INFINITY,maxV=maxU;
			for(var q : entry.getValue())
			{
				for(var p : q.positions())
				{
					minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);minZ=Math.min(minZ,p[2]);
					maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);maxZ=Math.max(maxZ,p[2]);
				}
				for(var uv : q.uvs())
				{
					minU=Math.min(minU,uv[0]);minV=Math.min(minV,uv[1]);
					maxU=Math.max(maxU,uv[0]);maxV=Math.max(maxV,uv[1]);
				}
			}
			System.out.printf("%s quads=%d pos=(%.3f,%.3f,%.3f)..(%.3f,%.3f,%.3f) uv=(%.3f,%.3f)..(%.3f,%.3f)%n",
					entry.getKey(), entry.getValue().size(), minX, minY, minZ, maxX, maxY, maxZ, minU, minV, maxU, maxV);
		}
	}
}
