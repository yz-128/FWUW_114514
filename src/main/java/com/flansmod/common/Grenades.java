package com.flansmod.common;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.flansmod.common.entity.EntityGrenade;
import com.flansmod.common.types.GrenadeType;

/**
 * The throwing half of a grenade definition, ported from 1.7.10's {@code ItemGrenade}.
 *
 * <p>1.7.10 put all of this in the item: {@code onItemRightClick} checked {@code CanThrow}, built
 * the entity, spawned it server-side, shrank the stack outside creative, and started the
 * definition's {@code ThrowDelay} timer. The port keeps the item as a thin dispatcher
 * ({@code ItemFlan.use}) and puts the shared behaviour here, so a grenade thrown by anything
 * other than a right-click on its own item — a placement helper, a scripted throw — goes through
 * the same checks.
 */
public final class Grenades
{
	private Grenades() { }

	/**
	 * Throws one grenade from {@code stack}, or does nothing when the definition forbids it.
	 *
	 * <p>The result is the side's usual success pair: the client gets {@code SUCCESS} so the arm
	 * swings and the predicted cooldown starts, the server gets {@code CONSUME} so the item is
	 * actually used. The entity itself is only spawned on the server; vanilla sends the spawn
	 * packet.
	 *
	 * @param level  the level the player is in
	 * @param player the thrower
	 * @param stack  the stack being thrown; one is consumed unless the player is in creative
	 * @param type   the definition the stack stands for
	 * @return {@code PASS} when nothing happened, otherwise the side's success result
	 */
	public static InteractionResult throwGrenade(Level level, Player player, ItemStack stack, GrenadeType type)
	{
		if(level == null || player == null || type == null || stack.isEmpty())
			return InteractionResult.PASS;
		// A definition that cannot be thrown is a placed utility object in 1.7.10's terms.
		if(!type.canThrow || player.isSpectator() || !player.isAlive())
			return InteractionResult.PASS;

		// The original blocked a second throw while data.shootTimeRight was still counting down.
		// ThrowDelay is that timer, expressed as the modern item cooldown so it gates both sides.
		if(type.throwDelay > 0 && player.getCooldowns().isOnCooldown(stack.getItem()))
			return InteractionResult.PASS;

		if(!level.isClientSide)
		{
			EntityGrenade grenade = new EntityGrenade(level, type, player);
			if(!level.addFreshEntity(grenade))
				return InteractionResult.PASS;

			// 1.7.10 :66-68 — one item per throw, and creative throws cost nothing.
			if(!player.getAbilities().instabuild)
				stack.shrink(1);

			// 1.7.10 :98-99 — the throw itself is heard (the constructor played it there; the
			// port keeps the side split clean by playing it where the server already is).
			playThrowSound(level, player, type);
		}

		// The cooldown is written on both sides: the client needs it to gate its predicted use
		// and draw the overlay, the server needs it to enforce the same delay in its own copy.
		if(type.throwDelay > 0)
			player.getCooldowns().addCooldown(stack.getItem(), type.throwDelay);

		// 1.7.10 swung the arm on throw. Doing it on both sides makes the local arm move at once
		// and every other client see the server's swing packet.
		player.swing(InteractionHand.MAIN_HAND);

		return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	/** Plays the definition's {@code ThrowSound} at the player, if it names one a pack ships. */
	private static void playThrowSound(Level level, Player player, GrenadeType type)
	{
		if(type.throwSound == null || type.throwSound.isBlank())
			return;
		SoundEvent event = FmumSounds.event(type.throwSound, 0F);
		if(event == null)
			return;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), event,
				SoundSource.PLAYERS, 4F, 1F);
	}
}
