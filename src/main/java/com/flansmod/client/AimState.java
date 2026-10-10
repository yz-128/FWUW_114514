package com.flansmod.client;

import net.minecraft.util.Mth;

/**
 * Whether the gun is being aimed, and how far the aim transition has run.
 *
 * <p>1.7.10 called this the zoom/ADS switch: while the aim button was held the first-person pose
 * moved the gun up and forward along {@code adsSwitch}, bringing the sights to the middle of the
 * screen. The port keeps the same 0..1 progress and smooths it, so the raise is animated rather
 * than a jump.
 */
public final class AimState
{
	private AimState() { }

	private static float progress, lastProgress;

	/** True once the raise has gone far enough that the resting pose should yield to ADS. */
	public static boolean isAiming()
	{
		return progress > 0.5F;
	}

	/** Advances the raise one tick. */
	public static void tick(boolean aiming)
	{
		lastProgress = progress;
		progress = Mth.clamp(progress + (aiming ? 0.2F : -0.2F), 0F, 1F);
	}

	/** Frame-interpolated progress, for the renderer. */
	public static float blend(float partialTick)
	{
		return lastProgress + (progress - lastProgress) * partialTick;
	}

	/** Back to rest, for when a world unloads. */
	public static void reset()
	{
		progress = lastProgress = 0F;
	}
}
