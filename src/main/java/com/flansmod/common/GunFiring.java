package com.flansmod.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ItemFlan;

/**
 * The shooting loop, ported from 1.7.10's {@code ItemGun.tryToShoot} and {@code onMouseHeld}.
 *
 * <p>The original drove this from two places: {@code onItemRightClick} handled a single click, and
 * a server tick handler called {@code onMouseHeld} every tick while the button was down, which is
 * how full-auto fire worked. Modern Minecraft splits the same idea across {@code use()} (the click)
 * and {@code onUseTick()} (called every tick while the item is in use), so the two older entry
 * points map onto those two.
 *
 * <p>What is deliberately kept from the original, because it decides how the gun feels:
 * <ul>
 *   <li>{@code shootTime} is both the rate-of-fire cooldown and the reload timer. A gun that fires
 *       and a gun that is reloading are in the same "busy" state, and one field expresses both.</li>
 *   <li>SEMI fires on the click only; BURST fires one round and counts the rest out over the
 *       following ticks; FULLAUTO fires every tick the trigger is held.</li>
 *   <li>Firing an empty gun starts a reload rather than doing nothing, which is the behaviour the
 *       packs were written against.</li>
 * </ul>
 */
public final class GunFiring
{
	private GunFiring() { }

	/**
	 * The trigger went down. Starts firing for the modes that fire once per press, and marks the
	 * trigger held so automatic fire keeps going from {@link #tickHeld}.
	 *
	 * <p>Left-click is the primary trigger, matching the 1.20.1 port's input mapping; the
	 * off-hand path stays available for one-handed guns through {@link #onUse}.
	 */
	public static void onTriggerPull(Level level, Player player, ItemStack stack, boolean mainHand)
	{
		if(!(stack.getItem() instanceof ItemFlan flan) || !(flan.type instanceof GunType gun))
			return;

		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data == null)
			return;

		boolean left = !mainHand;
		if(gun.modeFor(stack)==GunType.EnumFireMode.SAFE) { setShooting(data,left,false);data.pendingGunTrigger=false;return; }
		if(mainHand && gun.itsType==GunType.GunItsType.MAG) {
			data.isShootingRight=false;
			return;
		}
		if(mainHand) {
			GunActions.ensureHeld(player, data);
			if(!GunActions.ready(player, data)) {
				data.isShootingRight = data.pendingGunTrigger = true;
				data.gunAction.resume();
				return;
			}
		}
		// A fresh press resets the trigger; the tick handler raises it again while held.
		setShooting(data, left, true);

		// A press fires once for the modes that are not automatic. Burst counts its remaining
		// rounds out from here; semi is done after the one shot. FULLAUTO and MINIGUN fire from
		// the tick handler instead, so a press there only raises the trigger.
		GunType.EnumFireMode mode = gun.modeFor(stack);
		if(!mode.isAutomaticFire())
			tryToShoot(level, player, stack, gun, data, left, true);
		else if((left?data.shootTimeLeft:data.shootTimeRight)<=0F && (GunMagazine.findLoaded(stack,gun)<0
				|| (!gun.magazineNames.isEmpty() && GunMagazine.getChamberedRound(stack).isEmpty())))
			FmumSounds.play(player,gun.emptyClickSound,gun.emptyClickSoundRange,gun.distortSound,false);
	}

	/**
	 * The server's copy of the trigger edge, arriving from {@code TriggerPayload}.
	 *
	 * <p>The client predicts the same action locally; this is the authoritative run, and the one
	 * that persists the spent rounds and the reload timer to the saved game.
	 */
	public static void serverTrigger(Player player, boolean mainHand, boolean down)
	{
		PlayerData data = PlayerHandler.getServerData(player);
		if(data == null)
			return;

		if(mainHand)
			data.isShootingRight = down;
		else
			data.isShootingLeft = down;

		if(!down)
		{
			if(mainHand) data.pendingGunTrigger = false;
			// Released: a burst that was counting down ends here, as it did in the original.
			if(mainHand)
				data.burstRoundsRemainingRight = 0;
			else
				data.burstRoundsRemainingLeft = 0;
			return;
		}

		ItemStack stack = mainHand ? player.getMainHandItem() : player.getOffhandItem();
		onTriggerPull(player.level(), player, stack, mainHand);
	}

	/**
	 * The vanilla use button, kept as the off-hand trigger. The main hand's primary fire lives on
	 * left-click now, so this only starts anything when the off hand holds a one-handed gun.
	 */
	public static InteractionResultHolder<ItemStack> onUse(Level level, Player player, InteractionHand hand)
	{
		ItemStack stack = player.getItemInHand(hand);
		if(!(stack.getItem() instanceof ItemFlan flan) || !(flan.type instanceof GunType gun))
			return InteractionResultHolder.pass(stack);

		// The main-hand trigger is the attack key; the use button must not double-fire it. The
		// off-hand gun (the one-handed case) has no other input, so it keeps this one.
		if(hand != InteractionHand.OFF_HAND)
			return InteractionResultHolder.pass(stack);

		onTriggerPull(level, player, stack, false);

		// Deliberately NOT consume(): an item that reports itself as in-use is drawn by
		// ItemInHandRenderer through its EAT/BOW arm branch, which puts a gun several tenths of a
		// block below the screen. 1.7.10 had no such branch, so the trigger state is tracked in
		// PlayerData instead and the gun keeps the ordinary held-item transform.
		return InteractionResultHolder.pass(stack);
	}

	/**
	 * Called every tick while the use button is held, which is where 1.7.10's {@code onMouseHeld}
	 * on the server tick became.
	 */
	public static void tickHeld(Level level, Player player, ItemStack stack, GunType gun, boolean mainHand)
	{
		// A magazine is an item in the guns/ folder that FMUM's packs use to hold rounds; it is
		// not a firearm and must not fire. Without this guard every magazine the player holds
		// would try to shoot, which on a full-auto mag means an empty click every tick.
		if(gun.itsType == GunType.GunItsType.MAG) {
			return;
		}

		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data == null)
			return;

		boolean shooting = mainHand ? data.isShootingRight : data.isShootingLeft;
		if(!shooting)
		{
			// Trigger released: end any burst that was counting down.
			if(mainHand)
				data.burstRoundsRemainingRight = 0;
			else
				data.burstRoundsRemainingLeft = 0;
			return;
		}

		if(mainHand && data.pendingGunTrigger && GunActions.ready(player,data)) {
			data.pendingGunTrigger = false;
			onTriggerPull(level,player,stack,true);
			return;
		}
		switch(gun.modeFor(stack))
		{
			case FULLAUTO, MINIGUN -> tryToShoot(level, player, stack, gun, data, !mainHand, false);
			case BURST ->
			{
				// The first round went out on the click; the rest follow one per tick, in the way
				// the original counted them down.
				int remaining = mainHand ? data.burstRoundsRemainingRight : data.burstRoundsRemainingLeft;
				if(remaining > 0)
					tryToShoot(level, player, stack, gun, data, !mainHand, false);
			}
			default -> { }
		}
	}

	/** The trigger was released. */
	public static void onStopUsing(Player player, ItemStack stack, int remaining)
	{
		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data == null)
			return;
		data.isShootingRight = false;
		data.isShootingLeft = false;
	}

	private static void setShooting(PlayerData data, boolean left, boolean shooting)
	{
		if(left)
			data.isShootingLeft = shooting;
		else
			data.isShootingRight = shooting;
	}

	/**
	 * One attempt to fire. Does nothing while the gun is busy, and either fires a round, releases
	 * the burst, or starts a reload — the same decision tree the original used.
	 *
	 * @param firstRound true for the click that starts a semi or burst burst of fire
	 */
	private static void tryToShoot(Level level, Player player, ItemStack stack, GunType gun,
								   PlayerData data, boolean left, boolean firstRound)
	{
		float shootTime = left ? data.shootTimeLeft : data.shootTimeRight;
		if(player.containerMenu instanceof com.flansmod.common.inventory.GunModificationMenu) return;
		if(!GunAttachments.requirements(gun,stack,0)) return;
		if(!left && !GunActions.ready(player, data)) return;
		if(shootTime > 0)
			return;                                    // still cooling down, or mid-reload

		// 1.7.10 stored the rounds inside the gun as damageable item stacks and searched the
		// chambers here. Finding none, its next move was to start a reload rather than to do
		// nothing — a gun that runs dry while the trigger is held reloads itself.
		// Advanced load intentionally leaves the chamber untouched. A seated magazine alone
		// cannot fire until charge/release has fed the barrel.
		if(!gun.magazineNames.isEmpty() && GunMagazine.getChamberedRound(stack).isEmpty()
				&& GunMagazine.magazineType(stack,gun)!=null
				&& GunMagazine.roundsRemaining(GunMagazine.get(stack,gun,0))>0) {
			if(firstRound)FmumSounds.play(player,gun.emptyClickSound,gun.emptyClickSoundRange,gun.distortSound,false);
			return;
		}
		int chamber = GunMagazine.findLoaded(stack, gun);
		if(chamber < 0)
		{
			if(firstRound)FmumSounds.play(player,gun.emptyClickSound,gun.emptyClickSoundRange,gun.distortSound,false);
			startReload(level, player, gun, data, left, stack);
			return;
		}

		fireRound(level, player, stack, gun, data, left, chamber);
	}

	/**
	 * The firing-mode key: step the held gun to its next mode.
	 *
	 * <p>Only the server runs this for real. It reads the mode the stack is on, takes the next one
	 * the definition lists, and writes it back to the stack — so the choice is per-stack rather
	 * than per-definition, and is saved with the gun. 1.7.10 and the 1.20.1 build both do it this
	 * way, and both stop at the server so a client cannot pick a mode the pack never listed.
	 *
	 * <p>The client's own copy is updated by the same call when the player is in single player; on
	 * a server the change arrives back with the stack's data.
	 */
	public static void serverCycleFireMode(Player player, boolean offHand)
	{
		ItemStack stack = offHand ? player.getOffhandItem() : player.getMainHandItem();
		if(!(stack.getItem() instanceof ItemFlan flan) || !(flan.type instanceof GunType gun))
			return;

		if(!gun.canSwitchMode())
			return;

		GunType.EnumFireMode current = gun.modeFor(stack);
		GunType.EnumFireMode next = gun.nextMode(current);
		gun.setModeFor(stack, next);
		FmumSounds.play(player,gun.modeSwitchSound,gun.modeSwitchSoundRange);

		// A mode change cancels a burst that was counting down: the rounds it had left belong to
		// the old mode, and carrying them into the new one is how a three-round burst turns into a
		// one-round burst followed by two strays.
		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data != null)
		{
			if(offHand)
				data.burstRoundsRemainingLeft = 0;
			else
				data.burstRoundsRemainingRight = 0;
		}
	}

	/**
	 * The reload key: a player asked to reload, so refill whether the gun is empty or not.
	 *
	 * <p>1.7.10's key sent {@code PacketReload} to the server, which did exactly this — the
	 * {@code forceReload} flag skips the "chamber already loaded" check, which is the whole point
	 * of a manual reload. This entry point is side-aware: the server runs it when the request
	 * arrives, and the client runs it first as a prediction so the animation starts immediately.
	 */
	public static void requestReload(Player player, boolean offHand)
	{
		requestReload(player,offHand,com.flansmod.common.network.ReloadPayload.Action.NORMAL);
	}
	public static void requestReload(Player player,boolean offHand,com.flansmod.common.network.ReloadPayload.Action action)
	{
		ItemStack stack = offHand ? player.getOffhandItem() : player.getMainHandItem();
		if(!(stack.getItem() instanceof ItemFlan flan) || !(flan.type instanceof GunType gun))
			return;
		if(gun.itsType==GunType.GunItsType.MAG) return; // compression requires the held R request

		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data == null)
			return;

		// A reload that is already running blocks the request — the original gated the key on
		// the fire timer being expired.
		if(data.isReloading())
			return;
		if(!offHand && GunReloading.supports(gun) && player.level().isClientSide) return;
		if(!offHand && gun.itsType != GunType.GunItsType.MAG && !GunActions.ready(player, data)) {
			data.pendingGunReload = true;
			data.pendingReloadAction=action;
			data.pendingGunTrigger = false;
			data.gunAction.resume();
			return;
		}

		if(action!=com.flansmod.common.network.ReloadPayload.Action.NORMAL) {
			if(!offHand && gun.canForceReload && GunReloading.supports(gun)) GunReloading.start(player,data,stack,gun,1,action);
			return;
		}
		startReload(player.level(), player, gun, data, offHand, stack, true);
	}

	/**
	 * Starts a reload: pulls rounds from the inventory, then sets the timer and the flag.
	 *
	 * <p>This is {@code ItemGun.reload} plus the tail of {@code tryToShoot}. The ordering matters:
	 * the original loaded the gun first and only set the busy state if something actually went in,
	 * so a gun with no spare ammunition stays ready instead of locking up for a reload that loads
	 * nothing.
	 */
	private static boolean startReload(Level level, Player player, GunType gun, PlayerData data,
									   boolean left, ItemStack stack)
	{
		return startReload(level, player, gun, data, left, stack, false);
	}

	/** The body above; {@code force} is the manual-reload flag that skips the loaded-chamber check. */
	private static boolean startReload(Level level, Player player, GunType gun, PlayerData data,
									   boolean left, ItemStack stack, boolean force)
	{
		// A gun may forbid mid-magazine reloads; the flag is per-gun and the original rejected the
		// manual request outright when it was clear.
		if(force && !gun.canForceReload)
			return false;
		if(!left && GunReloading.supports(gun)) return GunReloading.start(player,data,stack,gun);

		if(!GunMagazine.reload(stack, gun, player, force))
			return false;
		GunActions.boltCaught(stack, false);

		// The reload timer is the same field as the rate-of-fire delay, so a reload blocks firing
		// for its whole duration — that is how the original expressed "busy". The duration comes
		// from effectiveReloadTime() because the FMUM packs express it as a list of step times
		// rather than one number; see that method.
		float reloadTime = gun.effectiveReloadTime();
		data.shootTimeRight = reloadTime;
		if(left)
			data.shootTimeLeft = reloadTime;

		if(left)
		{
			data.reloadingLeft = true;
			data.burstRoundsRemainingLeft = 0;
		}
		else
		{
			data.reloadingRight = true;
			data.burstRoundsRemainingRight = 0;
		}

		FmumSounds.play(player,gun.reloadSound,gun.reloadSoundRange);
		return true;
	}

	/** FMUM preCheckOnFire: stored cases occupy the chamber but cannot be fired. */
	public static com.flansmod.common.types.BulletType liveRound(ItemStack stack,GunType gun,int chamber) {
		if(gun.itsType==GunType.GunItsType.MAG || chamber<0) return null;
		ItemStack round=GunMagazine.getChamberedRound(stack);
		if(round.isEmpty()) round=GunMagazine.chamberedBullet(GunMagazine.get(stack,gun,chamber));
		return InfoType.getType(round) instanceof com.flansmod.common.types.BulletType bullet && !bullet.isCase ? bullet : null;
	}

	/** Fires one live round, leaving an unshootable case for manual extraction. */
	static void fireRound(Level level, Player player, ItemStack stack, GunType gun, PlayerData data,
						  boolean left, int chamber)
	{
		var loadedDefinition=liveRound(stack,gun,chamber);
		if(loadedDefinition==null) return;
		GunStats.Values stats=GunStats.effective(gun,stack);
		GunStats.Shot kick=level.isClientSide && !left?GunStats.shot(gun,stack,player.isShiftKeyDown(),
				level.random.nextFloat(),level.random.nextFloat()):null;
		// Spend the round. Both ammunition models go through the same call: it counts a magazine's
		// rounds out of the item's data (rolling onto the next magazine in the stack when one runs
		// dry) and a loose-round stack by size. This is the 1.20.1 build's consumeRound.
		ItemStack round = GunMagazine.get(stack, gun, chamber);
		ItemStack chambered=GunMagazine.getChamberedRound(stack);
		if(!chambered.isEmpty()) {
			GunMagazine.spendChamberedRound(stack,gun);
		} else if(!round.isEmpty())
		{
			// The round that is actually chambered decides what flies out — a gun that accepts two
			// ammunition types fires whichever one the player loaded, which is the whole reason
			// 1.7.10 stored item stacks in the gun rather than a count.
			GunMagazine.consumeRound(round);
			GunMagazine.set(stack, gun, chamber, GunMagazine.roundsRemaining(round) > 0
					|| GunMagazine.roundsPerItem(round) > 1 ? round : ItemStack.EMPTY);
		}
		if(!left && gun.releaseSlideTime > 0 && GunMagazine.isEmpty(stack, gun)) GunActions.boltCaught(stack, true);
		if(loadedDefinition!=null && kick!=null) data.recoilShots.addLast(kick);

		// Spawn the round(s). This is 1.7.10's ItemGun.shoot: one bullet entity per NumBullets,
		// spawned server-side only — the client learns about them through the normal entity spawn
		// packet, which is why nothing here is guarded by a side check beyond the level test.
		//
		// EFT core uses effective gun states, ADS/pellet spread, and the actual round's multiplier.
		if(!level.isClientSide && loadedDefinition != null)
		{
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
					new com.flansmod.common.network.GunShotPayload(player.getId(),gun.shortName,left,level.random.nextLong()));
			if(gun.itsType==GunType.GunItsType.AUTO_GUN && GunAttachments.requirements(gun,stack,1))GunCasings.eject(player,gun,GunCasings.casing(loadedDefinition),false);
			else if(!left && gun.itsType==GunType.GunItsType.BOLT_GUN) {
				data.boltChargeDelay=Math.max(0,gun.chargeDelay);data.boltChargeStack=stack;
			}
			// The handheld EntityBullet constructor halves spread. FMUM tryShoot does not add
			// vanilla Flan's 0.7 crouch spread multiplier; crouching affects recoil instead.
			float spread=GunStats.firingSpread(stats,loadedDefinition,data.gunAimDown && data.gunAimProgress>.5F);
			net.minecraft.world.phys.Vec3 origin = com.flansmod.common.entity.EntityBullet.muzzleOf(player);
			net.minecraft.world.phys.Vec3 direction = player.getLookAngle();
			for(int i = 0; i < Math.max(1, loadedDefinition.numBullets); i++)
				level.addFreshEntity(new com.flansmod.common.entity.EntityBullet(
						level, origin, direction, player, spread, stats.damage(),
						loadedDefinition, stats.speed(), gun));
		}

		// The shot delay is the fire-rate timer; for a burst this is also what arms the countdown
		// that {@link #onUseTick} walks down.
		if(left) data.shootTimeLeft=nextShotDelay(data.shootTimeLeft,stats.delay());
		else data.shootTimeRight=nextShotDelay(data.shootTimeRight,stats.delay());

		if(gun.modeFor(stack) == GunType.EnumFireMode.BURST && !left)
			data.burstRoundsRemainingRight = gun.numBurstRounds - 1;

		if(loadedDefinition!=null) {
			var sound=FmumSounds.shot(GunAttachments.view(gun,stack));
			FmumSounds.play(player,sound.name(),sound.range(),gun.distortSound,sound.silenced());
		}
	}
	/** Keep the fractional overshoot, as FMUM's shootTime += delay does. */
	public static float nextShotDelay(float remaining,float delay) { return Math.min(0F,remaining)+Math.max(1F,delay); }

}
