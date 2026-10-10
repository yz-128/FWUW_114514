package com.flansmod.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps one {@link PlayerData} per player and ticks it, the job 1.7.10's class of the same name did.
 *
 * <p>The maps are split by side rather than shared, which is how the original worked and is not an
 * accident: the two sides disagree about plenty of gun state while a shot is in flight, and the
 * client's copy exists to drive animation while the server's copy is the one that counts. They are
 * keyed by UUID — 1.7.10 used the username, which a player can change mid-session.
 *
 * <p>Ticking is driven by {@code PlayerTickEvent.Post} rather than by walking the world's player
 * list each tick: the event already fires once per player per tick on both sides, so the loop and
 * the side check both disappear.
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class PlayerHandler
{
	private PlayerHandler() { }

	/** State for players on a server, or in single-player's integrated server. */
	private static final Map<UUID, PlayerData> SERVER_DATA = new HashMap<>();
	/** State for players as the client sees them, including remote players. */
	private static final Map<UUID, PlayerData> CLIENT_DATA = new HashMap<>();

	/** The state for a player, created on first use, on the side that player is being handled on. */
	public static PlayerData getPlayerData(Player player)
	{
		if(player == null)
			return null;
		Map<UUID, PlayerData> map = player.level().isClientSide() ? CLIENT_DATA : SERVER_DATA;
		return map.computeIfAbsent(player.getUUID(), PlayerData::new);
	}

	/** The server-side state for a player, for code that must not touch client state. */
	public static PlayerData getServerData(Player player)
	{
		if(player == null)
			return null;
		return SERVER_DATA.computeIfAbsent(player.getUUID(), PlayerData::new);
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event)
	{
		getPlayerData(event.getEntity()).tick(event.getEntity());

		// The server drives held fire, the job 1.7.10's server tick handler did with onMouseHeld:
		// the client has already predicted its own copy for the animation, and this is the run that
		// spends the rounds the saved game keeps. Semi and burst first rounds come from the trigger
		// packet's press edge; everything after that comes from here, one round per tick.
		Player player = event.getEntity();
		if(player.level().isClientSide())
			return;

		PlayerData data = getServerData(player);
		ItemStack held = player.getMainHandItem();
		if(held.getItem() instanceof com.flansmod.common.types.ItemFlan flan
				&& flan.type instanceof com.flansmod.common.types.GunType gun)
		{
			if(data.isShootingRight)
				GunFiring.tickHeld(player.level(), player, held, gun, true);
		}
		else if(data.isShootingRight)
		{
			// The gun left the hand while the trigger was down; nothing should keep firing.
			data.isShootingRight = false;
			data.burstRoundsRemainingRight = 0;
		}
	}

	/** Drops a player's server state when they leave; 1.7.10 leaked these until the server stopped. */
	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
	{
		SERVER_DATA.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onPlayerDeath(LivingDeathEvent event)
	{
		if(event.getEntity() instanceof ServerPlayer player)
			getServerData(player).playerKilled();
	}
}
