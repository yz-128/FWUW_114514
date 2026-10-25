package com.flansmod.common;

/**
 * Barrel heat: the first data-driven gameplay mechanic added on top of the 1.7.10 port.
 *
 * <p>The pack-facing rules are deliberately gentle so the default is playable on any content pack:
 * a shot adds {@code heatPerShot} (default 1.2%), a barrel cools {@code heatCoolRate} per tick
 * (default 0.35%), and heat slows the fire rate slightly (default 10% slower at a completely full
 * barrel) before locking the trigger entirely. A barrel that
 * reaches full heat locks the trigger for {@code heatLockTicks}; the lock runs down on the player
 * tick and the heat keeps cooling, so the gun frees itself after a couple of seconds without any
 * new input.
 *
 * <p>Every value is a pure function of numbers, so {@code tools/offline/VerifyBarrelHeat} can test
 * the whole mechanic without a client, a world or a game launch. The switch is
 * {@code -Dflansmod.mechanics=off}, which keeps the 1.7.10 behaviour exactly (heat is ignored, no
 * lock, no multipliers, no rate penalty).
 */
public final class BarrelHeat
{
	/** Kept as an explicit kill switch for scripted runs; the mod config and data packs are the normal way. */
	private static final boolean FORCED_OFF =
			"off".equalsIgnoreCase(System.getProperty("flansmod.mechanics", "on"));

	private static final float MAX_HEAT = 1F;
	private static final float OVERHEAT = 1F;

	private BarrelHeat() { }

	public static boolean enabled()
	{
		return !FORCED_OFF && MechanicsSettings.barrelHeatEnabled();
	}

	/** A shot's worth of heat, clamped to a full barrel. */
	public static float afterShot(float heat, float perShot)
	{
		if(!enabled() || perShot <= 0F)
			return clamp(heat);
		return clamp(heat + perShot);
	}

	/** Cooling over a number of ticks; the caller supplies the gun's own rate. */
	public static float cool(float heat, float rate, int ticks)
	{
		if(!enabled() || heat <= 0F || rate <= 0F || ticks <= 0)
			return clamp(heat);
		return clamp(heat - rate * ticks);
	}

	public static boolean overheated(float heat)
	{
		return enabled() && heat >= OVERHEAT;
	}

	/** The trigger is dead while the barrel is at full heat or a lock is still running. */
	public static boolean blocked(float heat, int lockTicks)
	{
		return enabled() && (lockTicks > 0 || heat >= OVERHEAT);
	}

	/**
	 * The lock after a shot: when this shot took the barrel over the line the full lock is armed
	 * ({@code armTicks}, or more if one was already running), otherwise the running lock is handed
	 * back untouched. A normal shot must never arm anything — that was a bug where the fresh lock
	 * length was passed as the current one, so the first round locked the trigger.
	 */
	public static int lockOnOverheat(float heat, int lockTicks, int armTicks)
	{
		return overheated(heat) ? Math.max(lockTicks, Math.max(1, armTicks)) : lockTicks;
	}

	/** The shot-delay multiplier: 1.0 cold, the pack's {@code heatRateMult} at a full barrel. */
	public static float rateMultiplier(float heat, float maxMultiplier)
	{
		if(!enabled() || maxMultiplier <= 1F)
			return 1F;
		return 1F + (maxMultiplier - 1F) * clamp(heat);
	}

	public static float clamp(float heat)
	{
		if(heat < 0F)
			return 0F;
		return Math.min(heat, MAX_HEAT);
	}
}
