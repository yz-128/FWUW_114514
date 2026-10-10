package com.flansmod.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import com.flansmod.client.model.GunAnimations;
import com.flansmod.common.FlansMod;

/**
 * Holds and advances one {@link GunAnimations} per entity per hand.
 *
 * <p>1.7.10 kept these maps in its client proxy and ticked them from a client tick handler. The
 * split matters: animation state is per-<em>entity</em> rather than per-player, because the mod
 * draws guns held by other players and by its own entities, and each needs its own slide, pump and
 * reload progress.
 *
 * <p>Keyed by UUID instead of the entity object. The original used the entity as a map key, which
 * leaks an entry for every entity that ever held a gun until the map is swept; a UUID plus a sweep
 * on logout is a strictly better identity for the same purpose.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class GunAnimationRegistry
{
	private GunAnimationRegistry() { }

	private static final Map<UUID, GunAnimations> RIGHT = new ConcurrentHashMap<>();
	private static final Map<UUID, GunAnimations> LEFT = new ConcurrentHashMap<>();

	/** The animation state for an entity's given hand, created on first use. */
	public static GunAnimations get(LivingEntity entity, boolean offHand)
	{
		if(entity == null)
			return GunAnimations.DEFAULTS;
		return (offHand ? LEFT : RIGHT).computeIfAbsent(entity.getUUID(), k -> new GunAnimations());
	}

	/** The right-hand animation state for an entity; the path the renderer takes. */
	public static GunAnimations right(LivingEntity entity)
	{
		return get(entity, false);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event)
	{
		// Ticking every entry rather than only the local player's is what keeps other players'
		// guns animating while they are on screen.
		for(GunAnimations animation : RIGHT.values())
			animation.update();
		for(GunAnimations animation : LEFT.values())
			animation.update();
		com.flansmod.client.render.MuzzleEffectsRenderer.tick();
	}

	/** Forget entities when leaving a world, so a new world does not inherit their gun state. */
	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
	{
		RIGHT.clear();
		LEFT.clear();
		GunAnimationDriver.reset();
		com.flansmod.client.render.MuzzleEffectsRenderer.reset();
	}

	/** The local player's state, for client-side firing effects. */
	public static GunAnimations localPlayer(boolean offHand)
	{
		return Minecraft.getInstance().player == null
				? GunAnimations.DEFAULTS
				: get(Minecraft.getInstance().player, offHand);
	}
}
