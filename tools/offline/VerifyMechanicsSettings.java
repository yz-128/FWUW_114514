import com.flansmod.common.MechanicsSettings;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.zip.ZipFile;

/**
 * The mechanics settings layer: built-in defaults, the config layer, the data-pack override layer,
 * and the wiring that makes the mechanics read them.
 *
 * <p>Runs offline: it exercises {@link MechanicsSettings} directly, then inspects the final jar for
 * the config spec, the mod-list screen and the data-pack listener. It never loads the FML config
 * machinery, so it cannot fail because a config file is missing in this environment.
 */
public final class VerifyMechanicsSettings
{
	static int checks;
	static ZipFile jar;
	static void check(boolean ok, String message) { checks++; if(!ok) throw new AssertionError(message); }
	static void near(float actual, float expected, String message) {
		check(Math.abs(actual - expected) < .0001F, message + ": " + actual + " / " + expected);
	}
	static ClassNode node(String name) throws Exception {
		check(jar.getEntry(name + ".class") != null, "final JAR has " + name);
		var n = new ClassNode();
		try(var in = jar.getInputStream(jar.getEntry(name + ".class"))) { new ClassReader(in.readAllBytes()).accept(n, 0); }
		return n;
	}
	static boolean hasField(ClassNode n, String field) {
		for(var f : n.fields) if(f.name.equals(field)) return true;
		return false;
	}
	static boolean callsAnywhere(ClassNode n, String owner, String called) {
		for(var m : n.methods) for(var i : m.instructions)
			if(i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(called)) return true;
		return false;
	}
	static boolean referencesClass(ClassNode n, String internalName) {
		for(var m : n.methods) for(var i : m.instructions) {
			if(i instanceof TypeInsnNode t && t.desc.equals(internalName)) return true;
			if(i instanceof FieldInsnNode f && f.owner.equals(internalName)) return true;
			if(i instanceof MethodInsnNode c && c.owner.equals(internalName)) return true;
			// SomeClass.class is an LDC of an ASM Type in the constant pool, not a TypeInsnNode.
			if(i instanceof LdcInsnNode ldc && ldc.cst instanceof org.objectweb.asm.Type t
					&& t.getInternalName().equals(internalName)) return true;
		}
		return false;
	}

	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		jar = new ZipFile(args[0]);

		// ---------------------------------------------------------------- shipped defaults
		check(MechanicsSettings.masterEnabled(), "mechanics are on by default");
		check(MechanicsSettings.barrelHeatEnabled(), "barrel heat is on by default");
		check(!MechanicsSettings.blockBreakingEnabled(), "block breaking ships disabled");
		check(MechanicsSettings.legacyGlassBreaking(), "legacy glass breaking ships enabled and independent");
		near(MechanicsSettings.heatPerShotScale(), 1F, "heat per shot scale defaults to 1");
		near(MechanicsSettings.heatCoolRateScale(), 1.25F, "cooling scale defaults to 1.25");
		near(MechanicsSettings.healthPerHardness(), 4F, "health per hardness defaults to 4");
		near(MechanicsSettings.toughness(true, false, false, false), 5.05F, "dirt/sand toughness defaults to 5.05");
		near(MechanicsSettings.toughness(false, true, false, false), 0.30F, "glass toughness defaults to 0.30");
		near(MechanicsSettings.toughness(false, false, true, false), 3.05F, "wood toughness defaults to 3.05");
		near(MechanicsSettings.toughness(false, false, false, false), 5.60F, "stone toughness defaults to 5.60");
		near(MechanicsSettings.toughness(false, false, false, true), 10.0F, "metal toughness defaults to 10");
		check(MechanicsSettings.maxSoftBlocks() == 15, "soft block cap defaults to 15");
		check(MechanicsSettings.maxBlocks() == 25, "hard block cap defaults to 25");

		// The config spec's own defaults and the runtime defaults must be the same values, or a
		// fresh install would behave differently before and after the config file is generated.
		var spec = com.flansmod.common.config.MechanicsConfig.SPEC;
		check(com.flansmod.common.config.MechanicsConfig.ENABLED.getDefault() == MechanicsSettings.DEFAULT_ENABLED,
				"config default for enabled matches the runtime default");
		check(com.flansmod.common.config.MechanicsConfig.BARREL_HEAT.getDefault() == MechanicsSettings.DEFAULT_BARREL_HEAT,
				"config default for barrel heat matches");
		check(com.flansmod.common.config.MechanicsConfig.BLOCK_BREAKING.getDefault() == MechanicsSettings.DEFAULT_BLOCK_BREAKING,
				"config default for block breaking matches (shipped off)");
		near(com.flansmod.common.config.MechanicsConfig.HEAT_PER_SHOT_SCALE.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_HEAT_PER_SHOT_SCALE, "config heat scale default matches");
		near(com.flansmod.common.config.MechanicsConfig.HEAT_COOL_RATE_SCALE.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_HEAT_COOL_RATE_SCALE, "config cooling scale default matches");
		check(com.flansmod.common.config.MechanicsConfig.LEGACY_GLASS.getDefault()
				== MechanicsSettings.DEFAULT_LEGACY_GLASS, "config legacy glass default matches");
		near(com.flansmod.common.config.MechanicsConfig.HEALTH_PER_HARDNESS.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_HEALTH_PER_HARDNESS, "config health default matches");
		near(com.flansmod.common.config.MechanicsConfig.PLASTIC_TOUGHNESS.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_PLASTIC_TOUGHNESS, "config plastic default matches");
		near(com.flansmod.common.config.MechanicsConfig.GLASS_TOUGHNESS.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_GLASS_TOUGHNESS, "config glass default matches");
		near(com.flansmod.common.config.MechanicsConfig.WOOD_TOUGHNESS.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_WOOD_TOUGHNESS, "config wood default matches");
		near(com.flansmod.common.config.MechanicsConfig.STONE_TOUGHNESS.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_STONE_TOUGHNESS, "config stone default matches");
		near(com.flansmod.common.config.MechanicsConfig.METAL_TOUGHNESS.getDefault().floatValue(),
				MechanicsSettings.DEFAULT_METAL_TOUGHNESS, "config metal default matches");
		check(com.flansmod.common.config.MechanicsConfig.MAX_SOFT_BLOCKS.getDefault()
				== MechanicsSettings.DEFAULT_MAX_SOFT_BLOCKS, "config soft cap default matches");
		check(com.flansmod.common.config.MechanicsConfig.MAX_BLOCKS.getDefault()
				== MechanicsSettings.DEFAULT_MAX_BLOCKS, "config hard cap default matches");

		// ---------------------------------------------------------------- config layer
		MechanicsSettings.applyConfig(false, false, false, 2F, 0.5F, 8F, 7F, 0.2F, 1.2F, 2F, 3F, 2, 3, false);
		check(!MechanicsSettings.legacyGlassBreaking(), "legacy glass switch follows the config");
		check(!MechanicsSettings.masterEnabled(), "master switch follows the config");
		check(!MechanicsSettings.barrelHeatEnabled(), "barrel heat switch follows the config");
		check(!MechanicsSettings.blockBreakingEnabled(), "block breaking switch follows the config");
		near(MechanicsSettings.heatPerShotScale(), 2F, "heat scale follows the config");
		near(MechanicsSettings.healthPerHardness(), 8F, "health follows the config");
		near(MechanicsSettings.toughness(true, false, false, false), 7F, "plastic toughness follows the config");
		near(MechanicsSettings.toughness(false, true, false, false), 0.2F, "glass toughness follows the config");
		near(MechanicsSettings.toughness(false, false, false, true), 3F, "metal toughness follows the config");
		check(MechanicsSettings.maxSoftBlocks() == 2 && MechanicsSettings.maxBlocks() == 3,
				"caps follow the config");

		// ---------------------------------------------------------------- data-pack layer wins
		MechanicsSettings.setOverrides(Map.of(
				"enabled", true,
				"blockBreakingEnabled", true,
				"healthPerHardness", 6.0,
				"stoneToughness", 2.0,
				"metalToughness", 3.5,
				"maxBlocksPerShot", 9.0));
		MechanicsSettings.setOverrides(Map.of("legacyGlassBreaking", true));
		check(MechanicsSettings.legacyGlassBreaking(), "the data pack can re-enable legacy glass");
		MechanicsSettings.clearOverrides();
		MechanicsSettings.setOverrides(Map.of(
				"enabled", true,
				"blockBreakingEnabled", true,
				"healthPerHardness", 6.0,
				"stoneToughness", 2.0,
				"metalToughness", 3.5,
				"maxBlocksPerShot", 9.0));
		check(MechanicsSettings.masterEnabled(), "the data pack can turn the master switch back on");
		check(MechanicsSettings.blockBreakingEnabled(), "the data pack can re-enable block breaking");
		near(MechanicsSettings.healthPerHardness(), 6F, "the data pack overrides health");
		near(MechanicsSettings.toughness(false, false, false, false), 2F, "the data pack overrides stone");
		near(MechanicsSettings.toughness(false, false, false, true), 3.5F, "the data pack overrides metal");
		check(MechanicsSettings.maxBlocks() == 9, "the data pack overrides the hard block cap");
		MechanicsSettings.clearOverrides();
		MechanicsSettings.applyConfig(true, true, true, 1F, 1F, 4F, 5F, 0.35F, 1F, 1.5F, 2.5F, 4, 6, true);
		near(MechanicsSettings.healthPerHardness(), 4F, "clearing overrides falls back to the config layer");

		// ---------------------------------------------------------------- final-JAR wiring
		var config = node("com/flansmod/common/config/MechanicsConfig");
		check(hasField(config, "SPEC"), "the config spec exists");
		check(hasField(config, "ENABLED") && hasField(config, "HEALTH_PER_HARDNESS")
				&& hasField(config, "METAL_TOUGHNESS"), "the config exposes the documented knobs");
		var mod = node("com/flansmod/common/FlansMod");
		check(callsAnywhere(mod, "com/flansmod/common/config/MechanicsConfig", "register"),
				"the mod registers the config file");
		check(referencesClass(mod, "net/neoforged/neoforge/client/gui/IConfigScreenFactory"),
				"the mod registers the mod-list config screen factory");
		check(referencesClass(mod, "com/flansmod/client/gui/MechanicsConfigScreen"),
				"the config screen is reachable from the mod list");
		var data = node("com/flansmod/common/MechanicsData");
		check(callsAnywhere(data, "net/neoforged/neoforge/event/AddReloadListenerEvent", "addListener"),
				"the mod adds its listener to the server data reload");
		var listener = node("com/flansmod/common/MechanicsData$Listener");
		check("net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener".equals(listener.superName),
				"the data pack listener is a reload listener");
		check(callsAnywhere(listener, "com/flansmod/common/MechanicsSettings", "setOverrides"),
				"the data pack writes its overrides into the settings");
		var settings = node("com/flansmod/common/MechanicsSettings");
		check(callsAnywhere(settings, "com/flansmod/common/MechanicsSettings", "reapplyOverrides"),
				"config updates reapply the data-pack overrides");
		check(callsAnywhere(node("com/flansmod/common/BarrelHeat"), "com/flansmod/common/MechanicsSettings", "barrelHeatEnabled"),
				"barrel heat reads the switch from the settings");
		check(callsAnywhere(node("com/flansmod/common/entity/BlockDamage"), "com/flansmod/common/MechanicsSettings", "healthPerHardness"),
				"block health reads the settings");
		check(callsAnywhere(node("com/flansmod/common/entity/BulletBlockPenetration"), "com/flansmod/common/MechanicsSettings", "toughness"),
				"block toughness reads the settings");
		check(callsAnywhere(node("com/flansmod/common/entity/BulletBlockPenetration"), "com/flansmod/common/MechanicsSettings", "blockBreakingEnabled"),
				"the block-breaking switch gates the rules");

		System.out.println("PASS VerifyMechanicsSettings: " + checks + " assertions; defaults, config layer,"
				+ " data-pack override precedence and final-JAR wiring (config file, config screen, reload listener)."
				+ " No game/GPU/input.");
	}
}
