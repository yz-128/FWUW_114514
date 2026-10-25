package com.flansmod.common.entity;

import com.flansmod.common.MechanicsSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Progressive block destruction: a round chips a block instead of deleting it, and the block only
 * falls after enough damage has accumulated. This is what keeps a rifle round from erasing a stone
 * wall in one hit while still letting a heavy calibre open one in a couple of shots.
 *
 * <p>The model is split in two: the pure part ({@link #health}, {@link #chipped},
 * {@link #progress}, {@link #breakable}) is testable offline, and {@link #apply} is the thin
 * server-side state holder that also drives vanilla's crack overlay through
 * {@code ServerLevel.destroyBlockProgress}.
 *
 * <p>State is per server level and block position, expires after a few seconds so the map never
 * grows without bound, and is cleared when the block is finally broken.
 */
public final class BlockDamage
{
	/** Default hit points per unit of hardness; the live value comes from the mechanics settings. */
	public static final float HEALTH_PER_HARDNESS = 4F;
	/** Chips fade after this many ticks; a long pause should reset the damage. */
	public static final int CHIP_TICKS = 200;
	/** Foot-gun guard: never let the chip map grow without bound. */
	public static final int MAX_CHIPS = 8192;

	private record Chip(float damage, long tick) { }

	private static final Map<String, Chip> CHIPS = new HashMap<>();
	/** Stable crack id per block position: one entry per block instead of a new id every hit. */
	private static final int BREAKER_BASE = 0x40000000;

	private BlockDamage() { }

	// ---------------------------------------------------------------- pure model

	/** Hit points of the block: destroy time and material toughness decide the number of hits. */
	public static float health(float hardness, float toughness)
	{
		return Math.max(0.5F, hardness) * MechanicsSettings.healthPerHardness() * Math.max(0.05F, toughness);
	}

	/** The neutral-toughness form, for callers that only know hardness. */
	public static float health(float hardness)
	{
		return health(hardness, BulletBlockPenetration.TOUGHNESS_STONE);
	}

	/**
	 * Whether this hit finishes the block. A first hit never does unless the material is brittle
	 * (glass): the wall must crack visibly at least once, which is what the request asks for — a
	 * wooden wall or a stone block that vanishes in one shot shows no crack at all.
	 */
	public static boolean enough(float damageSoFar, float power, float health, boolean brittle)
	{
		if(damageSoFar + power < health)
			return false;
		return brittle || damageSoFar > 0F;
	}

	/** The old name, kept for the verifiers: a finishing hit without the brittle exception. */
	public static boolean chipped(float damageSoFar, float power, float health)
	{
		return damageSoFar > 0F && damageSoFar + power >= health;
	}

	/** Damage after a hit that did not finish the block. */
	public static float accumulated(float damageSoFar, float power)
	{
		return Math.max(0F, damageSoFar) + power;
	}

	/** The 0-9 crack stage vanilla draws for this much damage. */
	public static int progress(float damageSoFar, float health)
	{
		if(health <= 0F)
			return 9;
		int stage = (int)Math.floor(damageSoFar / health * 9F);
		// Stage 0 is vanilla's fully transparent crack: any chip must show at least stage 1.
		return Math.max(damageSoFar > 0F ? 1 : 0, Math.min(9, stage));
	}

	// ---------------------------------------------------------------- server side

	/**
	 * Applies one hit to the block and returns {@code true} when the block should fall now. Also
	 * updates the client's crack overlay, so the player sees the wall being shot apart.
	 *
	 * @param power the round's block power, from {@link BulletBlockPenetration#blockPower}
	 */
	public static boolean apply(ServerLevel level, BlockPos pos, BlockState state, float power)
	{
		float health = health(BulletBlockPenetration.hardness(state),
				BulletBlockPenetration.toughness(state));
		boolean brittle = BulletBlockPenetration.isBrittle(state);
		long now = level.getGameTime();
		String key = key(level, pos);

		if(CHIPS.size() > MAX_CHIPS)
			CHIPS.clear();
		Chip chip = CHIPS.get(key);
		if(chip != null && now - chip.tick() > CHIP_TICKS)
		{
			CHIPS.remove(key);
			// Remove the client's crack overlay as well; otherwise an expired chip leaves a
			// permanently cracked block behind.
			level.destroyBlockProgress(breakerId(pos), pos, -1);
			chip = null;
		}
		float damage = chip == null ? 0F : chip.damage();
		if(enough(damage, power, health, brittle))
		{
			CHIPS.remove(key);
			level.destroyBlockProgress(breakerId(pos), pos, 10);
			return true;
		}
		float next = accumulated(damage, power);
		CHIPS.put(key, new Chip(next, now));
		level.destroyBlockProgress(breakerId(pos), pos, progress(next, health));
		return false;
	}

	/** The damage a chip-only hit may accumulate: it stops just short of the block's health. */
	public static float chipOnlyDamage(float damageSoFar, float power, float health)
	{
		return Math.min(health * 0.95F, accumulated(damageSoFar, Math.max(0.01F, power)));
	}

	/**
	 * A hit the round has no chance of finishing (calibre/penetration too low, or block-breaking is
	 * off) still leaves a crack that grows toward nine and then stays: the player gets feedback and
	 * the block never breaks from these chips.
	 */
	public static void chipOnly(ServerLevel level, BlockPos pos, BlockState state, float power)
	{
		float health = health(BulletBlockPenetration.hardness(state),
				BulletBlockPenetration.toughness(state));
		long now = level.getGameTime();
		String key = key(level, pos);
		if(CHIPS.size() > MAX_CHIPS)
			CHIPS.clear();
		Chip chip = CHIPS.get(key);
		if(chip != null && now - chip.tick() > CHIP_TICKS)
		{
			CHIPS.remove(key);
			level.destroyBlockProgress(breakerId(pos), pos, -1);
			chip = null;
		}
		float damage = chip == null ? 0F : chip.damage();
		float next = chipOnlyDamage(damage, power, health);
		CHIPS.put(key, new Chip(next, now));
		level.destroyBlockProgress(breakerId(pos), pos, progress(next, health));
	}

	private static int breakerId(BlockPos pos)
	{
		return BREAKER_BASE | (pos.hashCode() & 0x0FFFFFFF);
	}

	/** A command or a block change should clear any pending chip. */
	public static void clear(ServerLevel level, BlockPos pos)
	{
		CHIPS.remove(key(level, pos));
	}

	private static String key(ServerLevel level, BlockPos pos)
	{
		return level.dimension().location() + "|" + pos.asLong();
	}
}
