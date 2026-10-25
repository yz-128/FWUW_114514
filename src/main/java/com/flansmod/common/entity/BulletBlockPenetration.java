package com.flansmod.common.entity;

import com.flansmod.common.MechanicsSettings;
import com.flansmod.common.types.BulletType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Block penetration for fired rounds: the rules tying a bullet's penetration level and calibre to
 * the blocks it can open and the damage that survives the cover.
 *
 * <p>Pure numbers so the mechanic is testable offline: {@link #canBreak}, {@link #remainingDamage}
 * and {@link #canContinue} take a hardness rather than a world. The bullet entity supplies the
 * state and performs the destruction; everything that decides <em>whether</em> and <em>how much</em>
 * is here.
 *
 * <p>Calibre comes from the pack's {@code AmmoType} tag ({@code 545x39}, {@code 762x51},
 * {@code 9x19}, {@code 338}, {@code 12G}, ...), so no content pack has to change: the tag already
 * exists for the ammo tooltip. A round with no tag behaves like a 7.62 reference round.
 */
public final class BulletBlockPenetration
{
	/** 7.62 mm is the neutral calibre: a round of that size gets a factor of 1. */
	public static final float REFERENCE_CALIBRE_MM = 7.62F;
	/** How hard a block may be, relative to the round's block power. */
	public static final float HARDNESS_SCALE = 0.6F;
	/** Budget in hardness units, relative to the hardest block the round can open. */
	public static final float BUDGET_SCALE = 2.5F;
	/** A single block can eat at most this share of the remaining entity damage. */
	public static final float MAX_DAMAGE_LOSS = 0.9F;
	/** Hard cap on how many blocks one round opens, so a heavy calibre cannot tunnel forever. */
	public static final int MAX_BLOCKS = 6;

	private BulletBlockPenetration() { }

	// ---------------------------------------------------------------- data

	/** Calibre in millimetres, read from the ammo tag the packs already carry. */
	public static float calibreMm(BulletType round)
	{
		String tag = round == null || round.ammoType == null ? "" : round.ammoType.trim().toLowerCase(Locale.ROOT);
		if(tag.isEmpty())
			return REFERENCE_CALIBRE_MM;
		if(tag.startsWith("12g"))
			return 12F;                       // 12 gauge is a bore size; treated as a big bore here.
		if(tag.startsWith("338"))
			return 8.6F;                      // .338 Lapua / Norma
		if(tag.startsWith("127"))
			return 12.7F;
		if(tag.startsWith("45acp") || tag.equals("45"))
			return 11.43F;
		int x = tag.indexOf('x');
		String head = x > 0 ? tag.substring(0, x) : tag;
		if(!head.isEmpty() && head.chars().allMatch(Character::isDigit))
		{
			int value = Integer.parseInt(head);
			int digits = head.length();
			if(digits == 3)
				return value / 100F;          // 545 -> 5.45, 762 -> 7.62, 556 -> 5.56
			if(digits == 2 && value >= 40)
				return value / 10F;           // 57x28 -> 5.7, 46x30 -> 4.6, 58x42 -> 5.8
			if(digits == 2)
				return value;                 // 12 -> 12
			return value;                     // 9x19 -> 9
		}
		return REFERENCE_CALIBRE_MM;
	}

	/** Bigger calibre hits harder; clamped so a huge bore cannot dwarf everything. */
	public static float calibreFactor(BulletType round)
	{
		float factor = calibreMm(round) / REFERENCE_CALIBRE_MM;
		return Math.max(0.5F, Math.min(2F, factor));
	}

	/** The pack's penetration bracket (the average of min/max), the same number cover attenuation uses. */
	public static float penetration(BulletType round)
	{
		return SoftBlockBallistics.penetrationLevel(round);
	}

	/** Penetration and calibre combined: the number block hardness is compared against. */
	public static float blockPower(BulletType round)
	{
		if(round == null)
			return 0F;
		return Math.max(0F, penetration(round) / 10F * calibreFactor(round));
	}

	/** The hardest block, in destroy-time units, this round can open. */
	public static float maxBreakableHardness(BulletType round)
	{
		return blockPower(round) * HARDNESS_SCALE;
	}

	/** Total hardness a round may spend opening blocks before it stops. */
	public static float blockBudget(BulletType round)
	{
		return maxBreakableHardness(round) * BUDGET_SCALE;
	}

	// ---------------------------------------------------------------- rules

	public static float hardness(BlockState state)
	{
		return state.getBlock().defaultDestroyTime();
	}

	public static boolean isLeaves(BlockState state)
	{
		// The tag is the runtime answer (data packs can add leaves); the class check also covers an
		// offline environment where no data pack has bound the tag yet.
		return state.is(BlockTags.LEAVES)
				|| state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock;
	}

	/** A round opens blocks whose hardness fits its power; unbreakable blocks never qualify. */
	public static boolean canBreak(BulletType round, float hardness)
	{
		return MechanicsSettings.blockBreakingEnabled() && round != null && round.breaksBlocks && hardness >= 0F
				&& maxBreakableHardness(round) > 0F && hardness <= maxBreakableHardness(round);
	}

	/** Hardness charged against the round's budget; a minimum keeps feather blocks from being free. */
	public static float cost(float hardness)
	{
		return Math.max(0.1F, hardness);
	}

	/**
	 * The share of entity damage lost when the round passes through a block of this hardness.
	 *
	 * <p>The share is relative to what this round can open at all: a block near the round's limit
	 * eats most of its damage (a 9 mm round through dirt, a 5.45 through stone), while a heavy
	 * round that opens the same block easily keeps most of its damage. That is the requested
	 * behaviour — lower penetration wastes far more damage on cover, higher penetration wastes very
	 * little.
	 */
	public static float damageLoss(float hardness, BulletType round)
	{
		float limit = maxBreakableHardness(round);
		if(limit <= 0F)
			return MAX_DAMAGE_LOSS;
		return Math.max(0F, Math.min(MAX_DAMAGE_LOSS, MAX_DAMAGE_LOSS * hardness / limit));
	}

	public static float remainingDamage(float damage, float hardness, BulletType round)
	{
		return damage * (1F - damageLoss(hardness, round));
	}

	/**
	 * Soft cover — the wood/wool blocks the pass-through tag already lets rounds cross — is opened
	 * much more easily than hard cover: a plank or a door only needs a quarter of the round's block
	 * power. The round still flies through it, so this does not turn a wooden wall into a wall.
	 */
	public static final float SOFT_HARDNESS_SCALE = 0.25F;
	public static final float SOFT_BUDGET_SCALE = 2F;
	public static final int MAX_SOFT_BLOCKS = 4;

	// ---------------------------------------------------------------- material toughness

	/**
	 * How many hits a block's material takes beyond its hardness. Loose, plastic ground (dirt,
	 * sand, gravel, clay, soul sand, mud, snow) swallows rounds and must be dug out, so it takes
	 * many hits; glass shatters almost at once; metal needs more than stone. Wood is the neutral
	 * 1.0 reference, matching the health a pack author would expect from its hardness alone.
	 */
	public static final float TOUGHNESS_PLASTIC = 5F;
	public static final float TOUGHNESS_GLASS = 0.35F;
	public static final float TOUGHNESS_WOOD = 1F;
	public static final float TOUGHNESS_METAL = 2.5F;
	public static final float TOUGHNESS_STONE = 1.5F;

	public static float toughness(BlockState state)
	{
		var block = state.getBlock();
		// The tags are the data-pack answer; the explicit blocks keep the classification working
		// without a data pack (offline verifiers) and cover the common vanilla cases exactly.
		boolean glass = state.is(BlockTags.IMPERMEABLE)
				|| block == net.minecraft.world.level.block.Blocks.GLASS
				|| block == net.minecraft.world.level.block.Blocks.GLASS_PANE
				|| block == net.minecraft.world.level.block.Blocks.TINTED_GLASS;
		boolean plastic = state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
				|| block == net.minecraft.world.level.block.Blocks.DIRT
				|| block == net.minecraft.world.level.block.Blocks.GRASS_BLOCK
				|| block == net.minecraft.world.level.block.Blocks.COARSE_DIRT
				|| block == net.minecraft.world.level.block.Blocks.PODZOL
				|| block == net.minecraft.world.level.block.Blocks.MYCELIUM
				|| block == net.minecraft.world.level.block.Blocks.ROOTED_DIRT
				|| block == net.minecraft.world.level.block.Blocks.FARMLAND
				|| block == net.minecraft.world.level.block.Blocks.DIRT_PATH
				|| block == net.minecraft.world.level.block.Blocks.MUD
				|| block == net.minecraft.world.level.block.Blocks.SAND
				|| block == net.minecraft.world.level.block.Blocks.RED_SAND
				|| block == net.minecraft.world.level.block.Blocks.GRAVEL
				|| block == net.minecraft.world.level.block.Blocks.CLAY
				|| block == net.minecraft.world.level.block.Blocks.SOUL_SAND
				|| block == net.minecraft.world.level.block.Blocks.SOUL_SOIL
				|| block == net.minecraft.world.level.block.Blocks.SNOW_BLOCK;
		boolean metal = state.is(BlockTags.BEACON_BASE_BLOCKS)
				|| block == net.minecraft.world.level.block.Blocks.IRON_BLOCK
				|| block == net.minecraft.world.level.block.Blocks.GOLD_BLOCK
				|| block == net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK
				|| block == net.minecraft.world.level.block.Blocks.EMERALD_BLOCK
				|| block == net.minecraft.world.level.block.Blocks.NETHERITE_BLOCK;
		boolean wood = state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS)
				|| state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
				|| state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS);
		return MechanicsSettings.toughness(plastic, glass, wood, metal);
	}

	/** Brittle materials (glass) are allowed to shatter on the first hit and skip the crack. */
	public static boolean isBrittle(BlockState state)
	{
		return toughness(state) < 0.5F;
	}

	/** Whether a pass-through block (wood, wool, doors, ...) can be opened by this round. */
	public static boolean canBreakSoft(BulletType round, float hardness)
	{
		return MechanicsSettings.blockBreakingEnabled() && round != null && round.breaksBlocks && hardness >= 0F
				&& blockPower(round) >= hardness * SOFT_HARDNESS_SCALE;
	}

	public static float softCost(float hardness)
	{
		return cost(hardness) * SOFT_HARDNESS_SCALE;
	}

	public static float softBudget(BulletType round)
	{
		return blockPower(round) * SOFT_BUDGET_SCALE;
	}

	/** Whether the round may open another soft block before its soft budget is used up. */
	public static boolean canContinueSoft(float spent, float hardness, int blocksBroken, BulletType round)
	{
		if(round == null || blocksBroken >= MechanicsSettings.maxSoftBlocks())
			return false;
		return spent + softCost(hardness) <= softBudget(round);
	}

	/** Whether the round still has budget and block slots to fly on through the hole it made. */
	public static boolean canContinue(float spent, float hardness, int blocksBroken, BulletType round)
	{
		if(round == null || blocksBroken >= MechanicsSettings.maxBlocks())
			return false;
		return spent + cost(hardness) <= blockBudget(round);
	}
}
