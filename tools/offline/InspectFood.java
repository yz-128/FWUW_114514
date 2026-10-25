import com.flansmod.common.Tools;
import com.flansmod.common.types.ToolType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the pack's food tools and prints the parsed nutrition/consumable state. */
public final class InspectFood
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
			if(tool.shortName == null || !tool.shortName.toLowerCase().startsWith("food_")) continue;
			System.out.printf("%-28s foodness=%d consumable=%s heal=%s amount=%.2f life=%d%n",
					tool.shortName, tool.foodness, Tools.isConsumable(tool), tool.healPlayers,
					tool.healAmount, tool.toolLife);
		}
	}
}
