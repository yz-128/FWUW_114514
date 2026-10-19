package com.flansmod.common;

import java.util.UUID;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The per-player state Flan's Mod keeps about guns, ported from 1.7.10's class of the same name.
 *
 * <p>This is the mod's shooting state machine, and 1.7.10 encoded it the way most small state
 * machines are encoded: not with an enum, but with a pair of timers whose values <em>are</em> the
 * state. {@code shootTime} above zero means "busy" — it is the rate-of-fire cooldown and the
 * reload duration at the same time — and {@code reloading} only says which animation to draw
 * while that timer runs. The transitions live in {@link #tick} (timers run down, the flag clears)
 * and in the shooting code that sets them.
 *
 * <p>Everything is tracked per hand, because 1.7.10 supported firing a one-handed gun in each.
 * Left is the "off hand" in that model, not the modern shield hand.
 *
 * <p>One thing deliberately left out: 1.7.10 also stored {@code PlayerSnapshot[]} here, a second
 * of past positions used for lag-compensated bullet hit detection. That belongs with the bullet
 * work and needs the hitbox classes to come across with it, so it is not here yet.
 */
public class PlayerData
{
	/** Whose state this is. 1.7.10 keyed by username; a player can change that, so this does not. */
	public final UUID playerUUID;

	// ---------------------------------------------------------------- shooting

	/**
	 * Rate-of-fire cooldown, and the reload timer: the same field means both, as in 1.7.10.
	 *
	 * <p>A float rather than an int because the delay is not always a whole number of ticks: a
	 * 900-round-per-minute gun fires every 1200/900 = 1.33 ticks, and rounding that to 1 would
	 * make it fire 20% too fast.
	 */
	public float shootTimeRight, shootTimeLeft;
	/** Local prediction queues successful shots, independently of cooldown and reload timers. */
	public final java.util.ArrayDeque<GunStats.Shot> recoilShots = new java.util.ArrayDeque<>();
	/** Stops a player shooting immediately after swapping weapons. */
	public int shootClickDelay;
	/** Original CHARGE_DELAY after a bolt-action shot; -1 means no pending charge. */
	public int boltChargeDelay=-1;
	public ItemStack boltChargeStack=ItemStack.EMPTY;
	/** Trigger held, for the hand it is held on. */
	public boolean isShootingRight, isShootingLeft;
	/** Reload animation is playing. Cleared when {@link #shootTimeRight} reaches zero. */
	public boolean reloadingRight, reloadingLeft;
	public final GunActionState gunAction = new GunActionState();
	/** Transient server-confirmed gesture, cleared on weapon/slot changes rather than saved on an item. */
	public int sightFoldUntilTick;
	public long sightFoldSequence;
	public boolean sightFolding(Player player) {return player.tickCount<sightFoldUntilTick;}
	public final GunReloadState gunReload = new GunReloadState();
	public com.flansmod.common.types.GunType reloadGun;
	public ItemStack reloadGunStack=ItemStack.EMPTY, reloadIncomingMagazine=ItemStack.EMPTY, reloadOutgoingMagazine=ItemStack.EMPTY;
	public int reloadSlot=-1,reloadIncomingSlot=-1;
	public boolean reloadEffectApplied,reloadSoundPlayed;
	public com.flansmod.common.network.ReloadPayload.Action reloadAction=com.flansmod.common.network.ReloadPayload.Action.NORMAL;
	public boolean compressionHeld;
	public int compressionSlot=-1;
	public com.flansmod.common.types.GunType compressionGun;
	public ItemStack compressionStack=ItemStack.EMPTY;
	public com.flansmod.common.network.ReloadPayload.Action pendingReloadAction=com.flansmod.common.network.ReloadPayload.Action.NORMAL;
	public com.flansmod.common.types.GunType actionGun;
	public int actionSlot = -1, actionTick = Integer.MIN_VALUE;
	public int actionAttachmentRevision = -1;
	public net.minecraft.world.level.Level actionLevel;
	public float effectiveTakeOut;
	public boolean pendingGunReload, pendingGunTrigger;
	public boolean gunAimDown;
	public float gunAimProgress;
	public com.flansmod.client.render.FmumCollisionRay.Ray gunWallRay;
	/** Rounds still to fire in the current burst. */
	public int burstRoundsRemainingLeft = 0, burstRoundsRemainingRight = 0;

	// ---------------------------------------------------------------- minigun / looping sounds

	/** Spool-up speed of a minigun, or zero. Rises while firing, decays every tick. */
	public float minigunSpeed = 0F;
	/** Counts down between repeats of a looping (minigun) firing sound. */
	public int loopedSoundDelay;
	/** Set when the loop stops, so the cooldown sound plays exactly once. */
	public boolean shouldPlayCooldownSound, shouldPlayWarmupSound;

	// ---------------------------------------------------------------- offhand gun

	/** The hotbar slot of the off-hand gun, 0 for none. */
	public int offHandGunSlot = 0;
	/** The off-hand gun stack, used when drawing another player's off-hand weapon. */
	public ItemStack offHandGunStack = ItemStack.EMPTY;

	// ---------------------------------------------------------------- melee

	/** Progress and length of a melee swing, in ticks. */
	public int meleeProgress, meleeLength;

	public PlayerData(UUID playerUUID)
	{
		this.playerUUID = playerUUID;
	}

	/**
	 * Advances the state machine by one tick. Called from both sides every tick, as 1.7.10 did.
	 */
	public void tick(Player player)
	{
		GunActions.tick(player, this);
		GunReloading.tick(player,this);
		// The timers are the state: a shot or a reload is "in progress" exactly as long as they
		// are above zero, so running them down is what ends both.
		if(shootTimeRight > 0)
			shootTimeRight-=1F;
		if(shootTimeRight <= 0 && !gunReload.active())
			reloadingRight = false;

		if(shootTimeLeft > 0)
			shootTimeLeft-=1F;
		if(shootTimeLeft <= 0)
			reloadingLeft = false;

		if(shootClickDelay > 0)
			shootClickDelay--;

		// Minigun spool: driven up while the trigger is held, then a leaky integrator bleeds it
		// back down, so it winds up and coasts down rather than switching instantly.
		if(isShootingRight && !reloadingRight)
			minigunSpeed += 2F;
		minigunSpeed *= 0.9F;

		if(loopedSoundDelay > 0)
		{
			loopedSoundDelay--;
			if(loopedSoundDelay == 0 && !isShootingRight)
				shouldPlayCooldownSound = true;
		}
	}

	/** Called when the player dies: drop anything that should not survive a respawn. */
	public void playerKilled()
	{
		boltChargeDelay=-1;
		boltChargeStack=ItemStack.EMPTY;
		recoilShots.clear();
		gunAction.reset(); actionGun = null; actionSlot = -1;
		gunReload.reset();reloadGun=null;reloadGunStack=reloadIncomingMagazine=reloadOutgoingMagazine=ItemStack.EMPTY;
		compressionHeld=false;compressionGun=null;compressionStack=ItemStack.EMPTY;compressionSlot=-1;
		reloadingRight=reloadingLeft=false;shootTimeRight=shootTimeLeft=0F;
		pendingGunReload = pendingGunTrigger = false;
		isShootingRight = isShootingLeft = false;
		meleeProgress = meleeLength = 0;
		minigunSpeed = 0F;
	}

	/** True while either hand is mid-reload. */
	public boolean isReloading()
	{
		return reloadingRight || reloadingLeft;
	}
}
