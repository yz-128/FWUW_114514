import com.flansmod.common.Tools;
import com.flansmod.common.types.ToolType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks the port's name-based medical classification against the real pack tools. */
public final class InspectMedTools
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
		for(var tool : ToolType.tools)
		{
			if(tool.shortName == null) continue;
			String name = tool.shortName.toLowerCase(java.util.Locale.ROOT);
			if(!(name.contains("salewa") || name.contains("firstaid") || name.contains("ifak")
					|| name.contains("grizzly") || name.contains("ai2") || name.contains("painkiller")
					|| name.contains("morphine") || name.contains("splint"))) continue;
			System.out.printf("%-24s name=%-24s emergency=%s painkiller=%s splint=%s heal=%.3f life=%d%n",
					tool.displayName(), name, Tools.isEmergencyKit(tool), Tools.isPainkiller(tool),
					Tools.isSplint(tool), tool.healAmount, tool.toolLife);
		}
	}
}
