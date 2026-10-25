package com.flansmod.client;

import com.flansmod.common.DebugCapture;
import com.flansmod.common.FlansMod;
import com.flansmod.common.network.GunModificationPayload;
import com.flansmod.client.render.RenderPerf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The compatibility round's scenes, captured after the normal self-verification sequence.
 *
 * <p>One run of {@code clientAuto -PflansCapture -PflansCompat} exercises four things the seven
 * optimisation mods (Iris, Sodium, ModernFix, FerriteCore, Lithium, Flerovium, SCLP) are most
 * likely to disturb:
 * <ol>
 *   <li>the gunsmith screen with its depth-guarded preview (tick 380 open, three captures);</li>
 *   <li>ADS, by forcing the aim state through the mod's own animation driver (ticks 440–484);</li>
 *   <li>a real grenade throw, spawned server-side by {@link DebugCapture} at tick 500, with
 *       frames during flight and after the explosion;</li>
 *   <li>a marker line, {@code [DebugCompat] done}, which the baseline runner waits for.</li>
 * </ol>
 *
 * <p>This is a debug harness: {@code flansmod.compatScenes} must be set, the kit comes from the
 * existing self-verification harness, no key or mouse event is synthesized, and the screenshots
 * go through vanilla's own {@code Screenshot.grab}. {@code flansmod.compatExit} closes the client
 * after the last frame so an unattended run ends on its own.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class DebugCompatScenes
{
	public static final boolean ENABLED = Boolean.getBoolean("flansmod.compatScenes");
	private static final boolean EXIT = Boolean.getBoolean("flansmod.compatExit");

	/** Opening the screen needs the gun in hand, which the kit has guaranteed since tick 60. */
	public static final int GUNSMITH_OPEN_AT_TICK = 380, GUNSMITH_CLOSE_AT_TICK = 424,
			AIM_FROM_TICK = 440, AIM_TO_TICK = 484, DONE_AT_TICK = 580;
	/** The grenade itself is thrown at this server tick by {@link DebugCapture}. */
	public static final int GRENADE_AT_TICK = 500;

	private static final int[] GUNSMITH_GRABS = { 392, 404, 416 };
	private static final int[] AIM_GRABS = { 452, 464, 476 };
	private static final int[] GRENADE_GRABS = { 512, 524, 536, 552 };

	private static int tick = -1;
	private static boolean forceAim;

	private DebugCompatScenes() { }

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event)
	{
		if(!ENABLED || !DebugCapture.ENABLED)
			return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || mc.level == null)
		{
			RenderPerf.scene("menu");
			tick = -1;
			forceAim = false;
			return;
		}
		if(tick < 0)
		{
			tick = 0;
			FlansMod.LOGGER.info("[DebugCompat] Joined; scenes: gunsmith "
					+ GUNSMITH_OPEN_AT_TICK + "-" + GUNSMITH_CLOSE_AT_TICK + ", ADS "
					+ AIM_FROM_TICK + "-" + AIM_TO_TICK + ", grenade " + GRENADE_AT_TICK
					+ ", done at " + DONE_AT_TICK);
		}
		tick++;
		// Exclude loading, kit changes, menu opening and aim transitions from comparisons.
		RenderPerf.scene(tick >= 340 && tick < GUNSMITH_OPEN_AT_TICK ? "world"
				: tick >= 392 && tick < GUNSMITH_CLOSE_AT_TICK && mc.screen instanceof com.flansmod.client.gui.GunModificationScreen ? "gunsmith"
				: tick >= 452 && tick < AIM_TO_TICK && AimState.blend(1F) >= 0.99F ? "ads"
				: tick >= 512 && tick < 560 ? "grenade" : "transition");

		if(tick == GUNSMITH_OPEN_AT_TICK)
		{
			// The binding's own path: the client asks the server to open the authoritative menu, so
			// the screen is built exactly as an ordinary I press would build it.
			PacketDistributor.sendToServer(GunModificationPayload.open());
			FlansMod.LOGGER.info("[DebugCompat] gunsmith: requested menu open");
		}
		if(captured(tick, GUNSMITH_GRABS))
			capture(mc, "flansmod_compat_gunsmith_t" + tick + ".png", "gunsmith screen=" + mc.screen);
		if(tick == GUNSMITH_CLOSE_AT_TICK)
		{
			if(mc.player != null)
				mc.player.closeContainer();
			mc.setScreen(null);
			FlansMod.LOGGER.info("[DebugCompat] gunsmith: closed");
		}

		if(tick == AIM_FROM_TICK)
		{
			forceAim = true;
			FlansMod.LOGGER.info("[DebugCompat] ads: aim forced through the animation driver");
		}
		if(captured(tick, AIM_GRABS))
			capture(mc, "flansmod_compat_ads_t" + tick + ".png",
					"ads blend=" + com.flansmod.client.AimState.blend(1F));
		if(tick == AIM_TO_TICK)
			forceAim = false;

		if(captured(tick, GRENADE_GRABS))
			capture(mc, "flansmod_compat_grenade_t" + tick + ".png", "grenade");

		if(tick == DONE_AT_TICK)
		{
			RenderPerf.dump();
			FlansMod.LOGGER.info("[DebugCompat] done");
			if(EXIT)
			{
				FlansMod.LOGGER.info("[DebugCompat] closing for the baseline runner");
				// Minecraft.close() is the same path an ordinary window close takes; calling
				// System.exit here instead races GLFW/OpenAL teardown and the process sometimes dies
				// with a Windows fast-fail (0xC0000409) instead of a clean status.
				try
				{
					mc.close();
				}
				catch(RuntimeException error)
				{
					FlansMod.LOGGER.warn("[DebugCompat] close failed: " + error);
					System.exit(0);
				}
			}
		}
	}

	/** True while the ADS scene wants the aim state driven without the use button. */
	public static boolean forceAim()
	{
		return ENABLED && forceAim;
	}

	private static boolean captured(int at, int[] ticks)
	{
		for(int t : ticks)
			if(at == t)
				return true;
		return false;
	}

	private static void capture(Minecraft mc, String name, String note)
	{
		CompatReport.refresh();
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
				msg -> FlansMod.LOGGER.info("[DebugCompat] " + msg.getString()));
		FlansMod.LOGGER.info("[DebugCompat] captured " + name + " (" + note + ")");
	}
}
