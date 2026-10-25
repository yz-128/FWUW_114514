import com.flansmod.common.MechanicsSettings;
import com.flansmod.common.entity.BlockDamage;
import com.flansmod.common.entity.BulletBlockPenetration;
import com.flansmod.common.entity.SoftBlockBallistics;
import com.flansmod.common.types.BulletType;
import com.flansmod.common.types.EnumType;
import com.flansmod.common.types.TypeFile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Bullet block penetration: calibre parsing, penetration/calibre block power, cover damage loss,
 * the leaf exemption and the final-JAR wiring.
 *
 * <p>No client, world or game launch: the rules are pure functions plus a bullet definition, and
 * the block hardness values come from vanilla's own registry after bootstrap.
 */
public final class VerifyBulletBlockPenetration
{
	static int checks;
	static ZipFile jar;
	static void check(boolean ok, String message) { checks++; if(!ok) throw new AssertionError(message); }
	static void near(float actual, float expected, float tolerance, String message) {
		check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " / " + expected);
	}
	static BulletType bullet(String name, String... lines) {
		var file = new TypeFile(EnumType.bullet, name, false);
		file.contentPack = "verification";
		file.lines.add("ShortName " + name);
		file.lines.addAll(List.of(lines));
		var type = new BulletType(file);
		type.read(file);
		return type;
	}
	static float hardness(net.minecraft.world.level.block.Block block) {
		return block.defaultBlockState().getBlock().defaultDestroyTime();
	}
	static ClassNode node(String name) throws Exception {
		check(jar.getEntry(name + ".class") != null, "final JAR has " + name);
		var n = new ClassNode();
		try(var in = jar.getInputStream(jar.getEntry(name + ".class"))) { new ClassReader(in.readAllBytes()).accept(n, 0); }
		return n;
	}
	static boolean callsAnywhere(ClassNode n, String owner, String called) {
		for(var m : n.methods) for(var i : m.instructions)
			if(i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(called)) return true;
		return false;
	}
	static boolean hasField(ClassNode n, String field) {
		for(var f : n.fields) if(f.name.equals(field)) return true;
		return false;
	}
	static boolean hasString(ClassNode n, String fragment) {
		for(var m : n.methods) for(var i : m.instructions)
			if(i instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains(fragment)) return true;
		return false;
	}

	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// The shipped default now has block breaking disabled; the rule assertions below describe
		// the enabled mechanic, so enable it explicitly and keep the historical test values.
		MechanicsSettings.applyConfig(true, true, true, 1F, 1F, 4F, 5F, 0.35F, 1F, 1.5F, 2.5F, 4, 6, true);
		jar = new ZipFile(args[0]);

		// ---------------------------------------------------------------- calibre from the ammo tag
		var p545 = bullet("cal545", "AmmoType 545x39", "PenetrationLevel 70 61");
		var p9 = bullet("cal9", "AmmoType 9x19", "PenetrationLevel 13 11");
		var p338 = bullet("cal338", "AmmoType 338", "PenetrationLevel 79 71");
		var p762 = bullet("cal762", "AmmoType 762x51", "PenetrationLevel 30 24");
		var p57 = bullet("cal57", "AmmoType 57x28", "PenetrationLevel 12 9");
		var p12g = bullet("cal12g", "AmmoType 12G", "PenetrationLevel 11 8");
		var p127 = bullet("cal127", "AmmoType 127x55", "PenetrationLevel 40 32");
		var p45 = bullet("cal45", "AmmoType 45acp", "PenetrationLevel 9 7");

		near(BulletBlockPenetration.calibreMm(p545), 5.45F, .001F, "545x39 is 5.45 mm");
		near(BulletBlockPenetration.calibreMm(p9), 9F, .001F, "9x19 is 9 mm");
		near(BulletBlockPenetration.calibreMm(p338), 8.6F, .001F, ".338 is 8.6 mm");
		near(BulletBlockPenetration.calibreMm(p762), 7.62F, .001F, "762x51 is 7.62 mm");
		near(BulletBlockPenetration.calibreMm(p57), 5.7F, .001F, "57x28 is 5.7 mm");
		near(BulletBlockPenetration.calibreMm(p12g), 12F, .001F, "12G is a big bore");
		near(BulletBlockPenetration.calibreMm(p127), 12.7F, .001F, "127x55 is 12.7 mm");
		near(BulletBlockPenetration.calibreMm(p45), 11.43F, .001F, "45 ACP is 11.43 mm");
		near(BulletBlockPenetration.calibreMm(null), 7.62F, .001F, "missing round falls back to 7.62");

		// Bigger calibre means a bigger factor, independent of penetration.
		check(BulletBlockPenetration.calibreFactor(p338) > BulletBlockPenetration.calibreFactor(p762),
				"a bigger bore has a bigger calibre factor");
		check(BulletBlockPenetration.calibreFactor(p9) > BulletBlockPenetration.calibreFactor(p545),
				"9 mm factor is above 5.45 mm");
		check(BulletBlockPenetration.calibreFactor(p12g) >= 1.5F, "a 12 gauge is a heavy calibre");

		// ---------------------------------------------------------------- block power
		float max338 = BulletBlockPenetration.maxBreakableHardness(p338);
		float max545 = BulletBlockPenetration.maxBreakableHardness(p545);
		float max9 = BulletBlockPenetration.maxBreakableHardness(p9);
		check(max338 > max545 && max545 > max9, "penetration and calibre order the block power: "
				+ max338 + " > " + max545 + " > " + max9);
		check(BulletBlockPenetration.blockPower(p338) > BulletBlockPenetration.blockPower(p545),
				"higher penetration is a stronger block breaker");

		// ---------------------------------------------------------------- real blocks
		float stone = hardness(Blocks.STONE), dirt = hardness(Blocks.DIRT), glass = hardness(Blocks.GLASS);
		float iron = hardness(Blocks.IRON_BLOCK), obsidian = hardness(Blocks.OBSIDIAN);
		check(stone > dirt && iron > stone && obsidian > iron, "vanilla hardness ordering sanity");
		check(BulletBlockPenetration.canBreak(p545, stone), "5.45 opens stone");
		check(!BulletBlockPenetration.canBreak(p545, iron), "5.45 does not open an iron block");
		check(BulletBlockPenetration.canBreak(p9, dirt) && BulletBlockPenetration.canBreak(p9, glass),
				"9 mm opens dirt and glass");
		check(!BulletBlockPenetration.canBreak(p9, stone), "9 mm does not open stone");
		check(BulletBlockPenetration.canBreak(p338, stone) && BulletBlockPenetration.canBreak(p338, iron),
				".338 opens stone and iron");
		check(!BulletBlockPenetration.canBreak(p338, obsidian), "even .338 does not open obsidian");
		check(!BulletBlockPenetration.canBreak(null, dirt), "no round cannot break blocks");
		var optOut = bullet("optOut", "AmmoType 762x51", "PenetrationLevel 70 61", "PenetratesBlocks false");
		check(!BulletBlockPenetration.canBreak(optOut, glass), "PenetratesBlocks false opts the round out");

		// ---------------------------------------------------------------- damage surviving cover
		float dirt9 = BulletBlockPenetration.damageLoss(dirt, p9);
		float dirt545 = BulletBlockPenetration.damageLoss(dirt, p545);
		float dirt338 = BulletBlockPenetration.damageLoss(dirt, p338);
		check(dirt9 > dirt545 && dirt545 > dirt338, "lower penetration loses far more damage on cover: "
				+ dirt9 + " > " + dirt545 + " > " + dirt338);
		near(BulletBlockPenetration.remainingDamage(10F, dirt, p9), 10F * (1F - dirt9), .002F,
				"remaining damage uses the same loss");
		check(BulletBlockPenetration.damageLoss(stone, p9) == BulletBlockPenetration.MAX_DAMAGE_LOSS,
				"cover the round cannot open still reports the maximum loss");
		check(BulletBlockPenetration.damageLoss(0F, p338) == 0F, "no hardness means no loss");
		check(BulletBlockPenetration.damageLoss(obsidian, p338) <= BulletBlockPenetration.MAX_DAMAGE_LOSS,
				"loss is capped");

		// ---------------------------------------------------------------- budget and block cap
		float budget545 = BulletBlockPenetration.blockBudget(p545);
		check(budget545 > BulletBlockPenetration.maxBreakableHardness(p545), "budget exceeds one block");
		float stoneCost = BulletBlockPenetration.cost(stone);
		check(BulletBlockPenetration.canContinue(budget545 - stoneCost - 0.05F, stone, 1, p545),
				"a budget with room for another stone still flies on");
		check(!BulletBlockPenetration.canContinue(budget545 - 0.05F, stone, 1, p545),
				"a budget without room for another stone stops the round");
		float budget9 = BulletBlockPenetration.blockBudget(p9);
		check(BulletBlockPenetration.canContinue(0F, dirt, 1, p9), "9 mm opens its first soft block");
		check(!BulletBlockPenetration.canContinue(budget9 - 0.05F, dirt, 1, p9),
				"a spent 9 mm budget stops the round");
		check(!BulletBlockPenetration.canContinue(0F, dirt, BulletBlockPenetration.MAX_BLOCKS, p338),
				"the block cap stops a heavy round");
		check(BulletBlockPenetration.canBreak(p338, stone) && BulletBlockPenetration.MAX_BLOCKS > 1,
				"the cap is reachable before infinity");

		// ---------------------------------------------------------------- leaves are ignored
		check(BulletBlockPenetration.isLeaves(Blocks.OAK_LEAVES.defaultBlockState()),
				"oak leaves are recognised as leaves");
		check(!BulletBlockPenetration.isLeaves(Blocks.STONE.defaultBlockState()),
				"stone is not leaves");
		check(SoftBlockBallistics.leavesResistance() == 0D, "leaves add no cover resistance");
		String tag = new String(jar.getInputStream(jar.getEntry("data/flansmod/tags/block/bullet_pass_through.json"))
				.readAllBytes(), StandardCharsets.UTF_8);
		check(tag.toLowerCase(Locale.ROOT).contains("#minecraft:leaves"),
				"the pass-through tag includes every leaf block");

		// ---------------------------------------------------------------- soft cover is opened
		float planks = hardness(Blocks.OAK_PLANKS), log = hardness(Blocks.OAK_LOG);
		float wool = hardness(Blocks.WHITE_WOOL);
		check(BulletBlockPenetration.canBreakSoft(p9, planks), "9 mm opens wooden planks");
		check(BulletBlockPenetration.canBreakSoft(p9, log), "9 mm opens a log");
		check(BulletBlockPenetration.canBreakSoft(p9, wool), "9 mm opens wool");
		check(BulletBlockPenetration.canBreakSoft(p545, planks), "5.45 opens planks too");
		check(!BulletBlockPenetration.canBreakSoft(optOut, planks), "PenetratesBlocks false also skips soft cover");
		check(!BulletBlockPenetration.canBreakSoft(null, planks), "no round opens nothing");
		check(!BulletBlockPenetration.canBreakSoft(p9, hardness(Blocks.OBSIDIAN)),
				"soft rule alone does not open obsidian (it is not a pass-through block, but the guard holds)");

		float softBudget9 = BulletBlockPenetration.softBudget(p9);
		float plankCost = BulletBlockPenetration.softCost(planks);
		check(BulletBlockPenetration.canContinueSoft(0F, planks, 0, p9), "the first soft block fits the budget");
		check(!BulletBlockPenetration.canContinueSoft(softBudget9 - 0.01F, planks, 0, p9),
				"a used-up soft budget stops the carving");
		check(!BulletBlockPenetration.canContinueSoft(0F, planks, BulletBlockPenetration.MAX_SOFT_BLOCKS, p338),
				"the soft block cap stops a heavy round");
		check(plankCost > 0F && softBudget9 > plankCost, "soft cost and budget are positive and ordered");

		// ---------------------------------------------------------------- progressive block damage
		float dirtHealth = BlockDamage.health(dirt, BulletBlockPenetration.toughness(Blocks.DIRT.defaultBlockState()));
		float stoneHealth = BlockDamage.health(stone, BulletBlockPenetration.toughness(Blocks.STONE.defaultBlockState()));
		float ironHealth = BlockDamage.health(iron, BulletBlockPenetration.toughness(Blocks.IRON_BLOCK.defaultBlockState()));
		float glassHealth = BlockDamage.health(glass, BulletBlockPenetration.toughness(Blocks.GLASS.defaultBlockState()));
		near(BulletBlockPenetration.toughness(Blocks.DIRT.defaultBlockState()),
				BulletBlockPenetration.TOUGHNESS_PLASTIC, .001F, "dirt is a plastic material");
		near(BulletBlockPenetration.toughness(Blocks.SAND.defaultBlockState()),
				BulletBlockPenetration.TOUGHNESS_PLASTIC, .001F, "sand is a plastic material");
		near(BulletBlockPenetration.toughness(Blocks.GLASS.defaultBlockState()),
				BulletBlockPenetration.TOUGHNESS_GLASS, .001F, "glass is brittle");
		near(BulletBlockPenetration.toughness(Blocks.IRON_BLOCK.defaultBlockState()),
				BulletBlockPenetration.TOUGHNESS_METAL, .001F, "iron is metal");
		near(BulletBlockPenetration.toughness(Blocks.STONE.defaultBlockState()),
				BulletBlockPenetration.TOUGHNESS_STONE, .001F, "stone is stone");
		check(dirtHealth > stoneHealth,
				"plastic ground outlasts stone despite its lower hardness (" + dirtHealth + " > " + stoneHealth + ")");
		near(dirtHealth, 10F, .001F, "dirt has 10 hit points (0.5 x 4 x 5)");
		near(stoneHealth, 9F, .001F, "stone has 9 hit points (1.5 x 4 x 1.5)");
		near(ironHealth, 50F, .001F, "an iron block has 50 hit points (5 x 4 x 2.5)");
		check(glassHealth < BlockDamage.HEALTH_PER_HARDNESS,
				"glass can be broken by almost anything");

		float power545 = BulletBlockPenetration.blockPower(p545);
		check(!BlockDamage.chipped(0F, power545, stoneHealth), "5.45 does not erase stone in one shot");
		check(BlockDamage.chipped(power545, power545, stoneHealth), "two 5.45 hits finish stone");
		check(!BlockDamage.chipped(0F, power545, dirtHealth),
				"one 5.45 hit does not finish plastic ground");
		check(!BlockDamage.chipped(power545, power545, dirtHealth),
				"two 5.45 hits do not finish plastic ground");
		check(BlockDamage.chipped(power545 * 2F, power545, dirtHealth),
				"a third 5.45 hit digs through plastic ground");
		float power9 = BulletBlockPenetration.blockPower(p9);
		check(!BlockDamage.chipped(0F, power9, stoneHealth) && power9 < stoneHealth,
				"9 mm chips but never finishes stone in one hit");
		check(!BlockDamage.chipped(power9 * 6F, power9, dirtHealth),
				"six 9 mm hits still leave plastic ground standing");
		check(BlockDamage.chipped(power9 * 7F, power9, dirtHealth),
				"the seventh 9 mm hit digs through plastic ground");
		check(BlockDamage.enough(0F, power9, glassHealth, true), "brittle glass shatters on the first hit");
		check(!BlockDamage.enough(0F, power9, glassHealth, false),
				"a non-brittle first hit never finishes the block (the crack must show)");
		float power338 = BulletBlockPenetration.blockPower(p338);
		check(!BlockDamage.chipped(0F, power338, ironHealth),
				"one .338 hit does not finish an iron block");
		check(!BlockDamage.chipped(power338 * 4F, power338, ironHealth),
				"five .338 hits do not finish an iron block");
		check(BlockDamage.chipped(power338 * 5F, power338, ironHealth),
				"the sixth .338 hit finishes the chipped iron block");
		near(BlockDamage.accumulated(1F, 2.5F), 3.5F, .001F, "chips accumulate");
		check(BlockDamage.progress(0F, stoneHealth) == 0, "no damage is crack stage 0");
		check(BlockDamage.progress(stoneHealth * 0.5F, stoneHealth) == 4, "half damage is crack stage 4");
		check(BlockDamage.progress(stoneHealth, stoneHealth) == 9, "full damage is crack stage 9");
		check(BlockDamage.CHIP_TICKS > 20, "chips survive a short pause");
		check(BlockDamage.MAX_CHIPS > 0, "the chip map has a guard");

		// Every non-brittle block must survive its first hit, so the crack is visible at least once.
		float planksHealth = BlockDamage.health(planks, BulletBlockPenetration.toughness(Blocks.OAK_PLANKS.defaultBlockState()));
		check(!BlockDamage.enough(0F, power338, planksHealth, false),
				"one .338 hit only cracks wooden planks");
		check(BlockDamage.enough(power338, power338, planksHealth, false),
				"the second .338 hit opens the planks");
		check(!BlockDamage.enough(0F, power545, stoneHealth, false), "one 5.45 hit only cracks stone");
		check(BlockDamage.enough(power545, power545, stoneHealth, false), "the second 5.45 hit opens stone");
		check(BlockDamage.progress(0.01F, stoneHealth) >= 1, "any chip is at least crack stage 1");
		check(BlockDamage.progress(0F, stoneHealth) == 0, "no chip is stage 0");
		float capped = BlockDamage.chipOnlyDamage(0F, power338, ironHealth);
		check(capped < ironHealth, "chip-only damage never reaches the block's health");
		check(capped > 0F, "chip-only damage still accumulates");
		float cappedAgain = BlockDamage.chipOnlyDamage(capped, power338, ironHealth);
		check(cappedAgain <= ironHealth * 0.951F, "chip-only damage stays capped");

		// ---------------------------------------------------------------- final-JAR wiring
		var bulletType = node("com/flansmod/common/types/BulletType");
		check(hasField(bulletType, "breaksBlocks"), "BulletType carries the block-breaking flag");
		check(hasString(bulletType, "PenetratesBlocks"), "PenetratesBlocks is a parsed key");
		var entity = node("com/flansmod/common/entity/EntityBullet");
		check(callsAnywhere(entity, "com/flansmod/common/entity/BulletBlockPenetration", "canBreak"),
				"the bullet asks the rules whether a block opens");
		check(callsAnywhere(entity, "com/flansmod/common/entity/BulletBlockPenetration", "canContinue"),
				"the bullet keeps flying while it has budget");
		check(callsAnywhere(entity, "com/flansmod/common/entity/BulletBlockPenetration", "remainingDamage"),
				"the bullet loses damage when it passes a block");
		check(callsAnywhere(entity, "com/flansmod/common/entity/BulletBlockPenetration", "isLeaves"),
				"the bullet explicitly ignores leaves");
		check(hasField(entity, "blockSpent") && hasField(entity, "blocksBroken"),
				"the bullet tracks its block budget and count");
		var rules = node("com/flansmod/common/entity/BulletBlockPenetration");
		check(hasField(rules, "HARDNESS_SCALE") && hasField(rules, "MAX_BLOCKS"),
				"the rules are constants, not magic numbers in the entity");
		var soft = node("com/flansmod/common/entity/SoftBlockBallistics");
		check(callsAnywhere(soft, "com/flansmod/common/entity/SoftBlockBallistics", "leavesResistance"),
				"the cover model asks for the leaf resistance instead of hard-coding it");
		check(callsAnywhere(entity, "com/flansmod/common/entity/BulletBlockPenetration", "canBreakSoft"),
				"a passed soft block is opened by the same rules object");
		check(hasField(entity, "softSpent") && hasField(entity, "softBlocks"),
				"the bullet tracks its soft block budget and count");
		check(callsAnywhere(entity, "com/flansmod/common/entity/BlockDamage", "apply"),
				"the bullet chips blocks through the progressive block damage model");
		check(callsAnywhere(entity, "com/flansmod/common/entity/BlockDamage", "chipOnly"),
				"blocks the round cannot break still show a crack without breaking");
		check(callsAnywhere(entity, "com/flansmod/common/MechanicsSettings", "blockBreakingEnabled"),
				"the bullet honours the block-breaking switch before chipping anything");
		check(callsAnywhere(entity, "com/flansmod/common/MechanicsSettings", "legacyGlassBreaking"),
				"the legacy glass break has its own switch");
		check(callsAnywhere(node("com/flansmod/common/entity/BlockDamage"),
						"com/flansmod/common/entity/BulletBlockPenetration", "isBrittle"),
				"the chip model asks for the brittle rule before finishing a first hit");
		var chips = node("com/flansmod/common/entity/BlockDamage");
		check(hasField(chips, "HEALTH_PER_HARDNESS") && hasField(chips, "CHIP_TICKS")
				&& hasField(chips, "MAX_CHIPS"), "the chip model keeps its constants");
		check(callsAnywhere(chips, "net/minecraft/server/level/ServerLevel", "destroyBlockProgress"),
				"the chip model drives vanilla's crack overlay");

		System.out.println("PASS VerifyBulletBlockPenetration: " + checks + " assertions; calibre from AmmoType,"
				+ " penetration/calibre block power, cover damage loss, leaf exemption and final-JAR wiring."
				+ " No game/GPU/input.");
	}
}
