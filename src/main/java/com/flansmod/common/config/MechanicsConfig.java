package com.flansmod.common.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mechanics settings file, visible from the mod list's Config button and editable in-game
 * through {@link com.flansmod.client.gui.MechanicsConfigScreen}.
 *
 * <p>Every value here is a global knob for the added gameplay mechanics (barrel heat, bullet
 * destruction of blocks). Content packs keep their own per-gun data; these settings scale or gate
 * the mechanics as a whole, and a data pack can override any of them through
 * {@code data/<namespace>/flansmod/mechanics/settings.json} without editing the config file.
 *
 * <p>Precedence at runtime is: data pack override &gt; config value &gt; built-in default. The
 * built-in defaults live in {@link com.flansmod.common.MechanicsSettings} and are also the values
 * the config is generated with, so a fresh install behaves exactly as before this feature existed.
 */
public final class MechanicsConfig
{
	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.ConfigValue<Boolean> ENABLED;
	public static final ModConfigSpec.ConfigValue<Boolean> BARREL_HEAT;
	public static final ModConfigSpec.ConfigValue<Boolean> HEAT_BAR;
	public static final ModConfigSpec.ConfigValue<Boolean> AMMO_HUD;
	public static final ModConfigSpec.ConfigValue<Boolean> FRACTURE;
	public static final ModConfigSpec.ConfigValue<Boolean> BLOCK_BREAKING;
	public static final ModConfigSpec.ConfigValue<Boolean> LEGACY_GLASS;
	public static final ModConfigSpec.ConfigValue<Double> HEAT_PER_SHOT_SCALE;
	public static final ModConfigSpec.ConfigValue<Double> HEAT_COOL_RATE_SCALE;
	public static final ModConfigSpec.ConfigValue<Double> HEALTH_PER_HARDNESS;
	public static final ModConfigSpec.ConfigValue<Double> PLASTIC_TOUGHNESS;
	public static final ModConfigSpec.ConfigValue<Double> GLASS_TOUGHNESS;
	public static final ModConfigSpec.ConfigValue<Double> WOOD_TOUGHNESS;
	public static final ModConfigSpec.ConfigValue<Double> STONE_TOUGHNESS;
	public static final ModConfigSpec.ConfigValue<Double> METAL_TOUGHNESS;
	public static final ModConfigSpec.ConfigValue<Integer> MAX_SOFT_BLOCKS;
	public static final ModConfigSpec.ConfigValue<Integer> MAX_BLOCKS;

	static
	{
		var builder = new ModConfigSpec.Builder();
		builder.comment("Gameplay mechanics added on top of the 1.7.10 port.",
				"Data packs may override every value through data/<namespace>/flansmod/mechanics/settings.json.")
				.push("mechanics");

		ENABLED = builder.comment("Master switch for every added mechanic.")
				.define("enabled", true);
		BARREL_HEAT = builder.comment("Barrel heat: shots warm the barrel, a hot barrel fires slower and locks at full heat.")
				.define("barrelHeatEnabled", true);
		HEAT_BAR = builder.comment("Show the port-only heat bar above the hotbar. 1.7.10 had no such HUD element, so it is hidden by default.")
				.define("heatBarEnabled", false);
		AMMO_HUD = builder.comment("Show the ammo panel beside the hotbar (rounds and chamber count). 1.7.10 had no such HUD element, so it is hidden by default.")
				.define("ammoHudEnabled", false);
		FRACTURE = builder.comment("Fall fractures: a 40% movement slowdown that splints cure and painkillers suppress.")
				.define("fractureEnabled", false);
		BLOCK_BREAKING = builder.comment("Bullets chip and eventually destroy blocks, using penetration level and calibre.")
				.define("blockBreakingEnabled", false);
		LEGACY_GLASS = builder.comment("The packs' original BreaksGlass rounds still shatter glass when this is on.")
				.define("legacyGlassBreaking", true);

		builder.comment("Multipliers over each gun's own HeatPerShot / HeatCoolRate values.").push("barrelHeat");
		HEAT_PER_SHOT_SCALE = builder.defineInRange("heatPerShotScale", 1.0D, 0.0D, 5.0D);
		HEAT_COOL_RATE_SCALE = builder.defineInRange("heatCoolRateScale", 1.25D, 0.0D, 5.0D);
		builder.pop();

		builder.comment("Block hit points and per-material toughness.").push("blockBreaking");
		HEALTH_PER_HARDNESS = builder.comment("Hit points per unit of block hardness: stone (1.5) gets 6 at the default 4.")
				.defineInRange("healthPerHardness", 4.0D, 0.5D, 20.0D);
		PLASTIC_TOUGHNESS = builder.comment("Dirt, sand, gravel, clay, soul sand, snow: rounds are swallowed, many hits needed.")
				.defineInRange("plasticToughness", 5.05D, 0.05D, 20.0D);
		GLASS_TOUGHNESS = builder.comment("Glass shatters almost at once.")
				.defineInRange("glassToughness", 0.30D, 0.05D, 10.0D);
		WOOD_TOUGHNESS = builder.defineInRange("woodToughness", 3.05D, 0.05D, 20.0D);
		STONE_TOUGHNESS = builder.defineInRange("stoneToughness", 5.60D, 0.05D, 20.0D);
		METAL_TOUGHNESS = builder.comment("Iron, gold, diamond, emerald and netherite blocks.")
				.defineInRange("metalToughness", 10.0D, 0.05D, 20.0D);
		MAX_SOFT_BLOCKS = builder.comment("Soft (wood/wool) blocks one round may chip.")
				.defineInRange("maxSoftBlocksPerShot", 15, 1, 64);
		MAX_BLOCKS = builder.comment("Hard blocks one round may open before it stops.")
				.defineInRange("maxBlocksPerShot", 25, 1, 64);
		builder.pop();
		builder.pop();

		SPEC = builder.build();
	}

	private MechanicsConfig() { }

	/** Called from the mod constructor: registers the file and makes it editable from the mod list. */
	public static void register(ModContainer container)
	{
		container.registerConfig(ModConfig.Type.COMMON, SPEC);
	}

	/** Reads the config into the runtime settings; safe before the config file is loaded. */
	public static void reload()
	{
		if(!SPEC.isLoaded())
			return;
		try
		{
			com.flansmod.common.MechanicsSettings.applyConfig(
					ENABLED.get(), BARREL_HEAT.get(), BLOCK_BREAKING.get(),
					HEAT_PER_SHOT_SCALE.get().floatValue(), HEAT_COOL_RATE_SCALE.get().floatValue(),
					HEALTH_PER_HARDNESS.get().floatValue(),
					PLASTIC_TOUGHNESS.get().floatValue(), GLASS_TOUGHNESS.get().floatValue(),
					WOOD_TOUGHNESS.get().floatValue(), STONE_TOUGHNESS.get().floatValue(),
					METAL_TOUGHNESS.get().floatValue(),
					MAX_SOFT_BLOCKS.get(), MAX_BLOCKS.get(), LEGACY_GLASS.get(), HEAT_BAR.get(),
					AMMO_HUD.get());
			com.flansmod.common.MechanicsSettings.setFractureEnabled(FRACTURE.get());
		}
		catch(RuntimeException ignored)
		{
			// The file is not loaded yet (or a data pack is mid-reload); built-in defaults stay.
		}
	}
}
