package com.flansmod.common.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The arithmetic of a thrown grenade, kept out of the entity so it can be checked without a
 * world: gravity, the bounce and the two time gates are pure functions of vectors and numbers.
 *
 * <p>Everything here is transcribed from 1.7.10's {@code EntityGrenade} (FMUM), which is the
 * behaviour reference for this port. The original worked in floats and moved the entity by hand;
 * the formulas below keep its constants and its order of operations rather than re-deriving what
 * a "correct" bounce would look like, because the packs were balanced against these numbers.
 */
public final class GrenadeBallistics
{
	/**
	 * 1.7.10's gravity per tick: 9.81 blocks per second squared divided by 400.
	 *
	 * <p>The original wrote {@code motionY -= 9.81D / 400D * type.fallSpeed} at the end of every
	 * tick, so this is a velocity step, not an acceleration applied elsewhere.
	 */
	public static final double GRAVITY = 9.81D / 400D;

	/** 1.7.10's "the motion is too small to matter" threshold, used for the bounce's lambda. */
	private static final double EPSILON = 0.00000001D;

	private GrenadeBallistics() { }

	/**
	 * One tick of gravity, scaled by the definition's {@code FallSpeed}.
	 *
	 * <p>1.7.10 applied this after moving, so a grenade falls from the second tick onwards rather
	 * than being pulled down on the tick it leaves the hand. Callers keep that order.
	 */
	public static Vec3 applyGravity(Vec3 motion, float fallSpeed)
	{
		return motion.add(0D, -GRAVITY * (double)fallSpeed, 0D);
	}

	/**
	 * The motion after one bounce, as 1.7.10 computed it.
	 *
	 * <p>The original split the tick's motion at the contact point: {@code pre} carried the
	 * grenade to the hit and {@code post} is the part not yet travelled. It mirrored {@code post}
	 * on the face's axis, divided it by lambda — the ratio of the reflected length to the full
	 * tick motion, which is the time left in the tick — applied {@code Bounciness / 2}, and used
	 * the result divided by lambda as the new velocity. A lambda of zero means the reflection
	 * cancels the motion exactly and the original's division would be infinite; a stopped grenade
	 * is the only sensible answer there.
	 *
	 * @param motion     the tick's motion, before the hit
	 * @param from       where the grenade was at the start of the tick
	 * @param hit        the contact point the world's ray trace returned
	 * @param face       the block face that was hit
	 * @param bounciness the definition's {@code Bounciness}
	 * @return the new motion, or {@link Vec3#ZERO} when the bounce cancels it
	 */
	public static Vec3 bounce(Vec3 motion, Vec3 from, Vec3 hit, Direction face, float bounciness)
	{
		Vec3 post = reflectedPost(motion, from, hit, face);
		// 1.7.10 :302 — lambda is measured before Bounciness is applied.
		double lambda = motion.lengthSqr() < EPSILON ? 1D : post.length() / motion.length();
		if(lambda == 0D)
			return Vec3.ZERO;
		// 1.7.10 :304/312-314 — scale, then undo the time-scale so the speed is in ticks again.
		return post.scale((double)bounciness / 2D).scale(1D / lambda);
	}

	/**
	 * Where the grenade ends the tick it bounced, as 1.7.10 positioned it.
	 *
	 * <p>The original moved the entity by {@code pre + post} (1.7.10 :307-309), with {@code post}
	 * already mirrored and already scaled by {@code Bounciness / 2}; it had not yet been divided by
	 * lambda, because the position and the motion use different time scales.
	 *
	 * @return the new position after the bounce
	 */
	public static Vec3 bouncePosition(Vec3 from, Vec3 hit, Vec3 motion, Direction face, float bounciness)
	{
		Vec3 pre = hit.subtract(from);
		Vec3 post = reflectedPost(motion, from, hit, face);
		return from.add(pre).add(post.scale((double)bounciness / 2D));
	}

	/**
	 * The part of the motion past the contact point, mirrored on the face's axis.
	 *
	 * <p>1.7.10 :294-299 negated Y for a floor/ceiling hit, X for an east/west hit and Z for a
	 * north/south hit; a modern {@link Direction} carries the same axis, so the switch is on that.
	 */
	private static Vec3 reflectedPost(Vec3 motion, Vec3 from, Vec3 hit, Direction face)
	{
		Vec3 post = motion.subtract(hit.subtract(from));
		if(face == null)
			return post;
		return switch(face.getAxis())
		{
			case X -> new Vec3(-post.x, post.y, post.z);
			case Y -> new Vec3(post.x, -post.y, post.z);
			case Z -> new Vec3(post.x, post.y, -post.z);
		};
	}

	/**
	 * Whether the fuse has run out.
	 *
	 * <p>1.7.10 :210 compared {@code ticksExisted > type.fuse}, strictly: a {@code Fuse 70}
	 * grenade explodes on tick 71, never on the tick it reaches 70. A fuse of zero or less means
	 * the definition has no timer and the grenade only detonates by impact or another trigger.
	 */
	public static boolean detonatesByFuse(int age, int fuse)
	{
		return fuse > 0 && age > fuse;
	}

	/**
	 * Whether the charge is primed.
	 *
	 * <p>1.7.10 :525 returned from {@code detonate()} while {@code ticksExisted < primeDelay}, so
	 * the charge is armed once the age reaches the delay — and a delay of zero is armed at once.
	 */
	public static boolean armed(int age, int primeDelay)
	{
		return age >= primeDelay;
	}
}
