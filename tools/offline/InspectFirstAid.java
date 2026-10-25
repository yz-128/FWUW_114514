import com.flansmod.common.types.ToolType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the Misc-style tools and prints the parsed first-aid fields. */
public final class InspectFirstAid
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
			if(tool.shortName != null && tool.shortName.toLowerCase().contains("firstaid"))
				System.out.printf("%s heal=%s amount=%.3f life=%d destroy=%s%n",
						tool.shortName, tool.healPlayers, tool.healAmount, tool.toolLife, tool.destroyOnEmpty);
	}
}
