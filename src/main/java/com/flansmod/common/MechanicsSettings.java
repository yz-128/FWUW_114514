package com.flansmod.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime value store for the added mechanics: built-in defaults, overlaid by the config file,
 * overlaid again by a data pack.
 *
 * <p>The mechanics read this class instead of hard-coded constants, so a server owner can tune
 * them from {@code config/flansmod-common.toml} or from a data pack without rebuilding, and a
 * player can flip the switches from the in-game config screen.
 *
 * <p>Keep this class free of config-loader imports in its static defaults: the offline verifiers
 * boot the game classes without FML and must see the same defaults a fresh install gets.
 */
public final class MechanicsSettings
{
	// Built-in defaults. These are also the defaults the config file is generated with.
	public static final boolean DEFAULT_ENABLED = true;
	public static final boolean DEFAULT_BARREL_HEAT = true;
	/** The heat bar is a port-only HUD element; 1.7.10 had no such readout, so default off. */
	public static final boolean DEFAULT_HEAT_BAR = false;
	/** The ammo-panel beside the hotbar is also a port-only readout; default off. */
	public static final boolean DEFAULT_AMMO_HUD = false;
	/** Fall fracture: the port-owned injury used by splints and painkillers. */
	public static final boolean DEFAULT_FRACTURE = false;
	public static final boolean DEFAULT_BLOCK_BREAKING = false;
	public static final boolean DEFAULT_LEGACY_GLASS = true;
	public static final float DEFAULT_HEAT_PER_SHOT_SCALE = 1F;
	public static final float DEFAULT_HEAT_COOL_RATE_SCALE = 1.25F;
	public static final float DEFAULT_HEALTH_PER_HARDNESS = 4F;
	public static final float DEFAULT_PLASTIC_TOUGHNESS = 5.05F;
	public static final float DEFAULT_GLASS_TOUGHNESS = 0.30F;
	public static final float DEFAULT_WOOD_TOUGHNESS = 3.05F;
	public static final float DEFAULT_STONE_TOUGHNESS = 5.60F;
	public static final float DEFAULT_METAL_TOUGHNESS = 10.0F;
	public static final int DEFAULT_MAX_SOFT_BLOCKS = 15;
	public static final int DEFAULT_MAX_BLOCKS = 25;

	private static boolean enabled = DEFAULT_ENABLED;
	private static boolean barrelHeat = DEFAULT_BARREL_HEAT;
	private static boolean heatBar = DEFAULT_HEAT_BAR;
	private static boolean ammoHud = DEFAULT_AMMO_HUD;
	private static boolean fracture = DEFAULT_FRACTURE;
	private static boolean blockBreaking = DEFAULT_BLOCK_BREAKING;
	private static boolean legacyGlass = DEFAULT_LEGACY_GLASS;
	private static float heatPerShotScale = DEFAULT_HEAT_PER_SHOT_SCALE;
	private static float heatCoolRateScale = DEFAULT_HEAT_COOL_RATE_SCALE;
	private static float healthPerHardness = DEFAULT_HEALTH_PER_HARDNESS;
	private static float plasticToughness = DEFAULT_PLASTIC_TOUGHNESS;
	private static float glassToughness = DEFAULT_GLASS_TOUGHNESS;
	private static float woodToughness = DEFAULT_WOOD_TOUGHNESS;
	private static float stoneToughness = DEFAULT_STONE_TOUGHNESS;
	private static float metalToughness = DEFAULT_METAL_TOUGHNESS;
	private static int maxSoftBlocks = DEFAULT_MAX_SOFT_BLOCKS;
	private static int maxBlocks = DEFAULT_MAX_BLOCKS;

	/** Data-pack overrides, keyed by the same names the config uses. */
	private static final Map<String, Object> OVERRIDES = new HashMap<>();

	private MechanicsSettings() { }

	// ---------------------------------------------------------------- config layer

	/** Called by the config loader. Values are also the base a data pack overrides. */
	public static synchronized void applyConfig(boolean enabledIn, boolean barrelHeatIn, boolean blockBreakingIn,
			float heatPerShotScaleIn, float heatCoolRateScaleIn, float healthPerHardnessIn,
			float plasticToughnessIn, float glassToughnessIn, float woodToughnessIn,
			float stoneToughnessIn, float metalToughnessIn, int maxSoftBlocksIn, int maxBlocksIn,
			boolean legacyGlassIn)
	{
		applyConfig(enabledIn, barrelHeatIn, blockBreakingIn, heatPerShotScaleIn, heatCoolRateScaleIn,
				healthPerHardnessIn, plasticToughnessIn, glassToughnessIn, woodToughnessIn, stoneToughnessIn,
				metalToughnessIn, maxSoftBlocksIn, maxBlocksIn, legacyGlassIn, DEFAULT_HEAT_BAR);
	}

	public static synchronized void applyConfig(boolean enabledIn, boolean barrelHeatIn, boolean blockBreakingIn,
			float heatPerShotScaleIn, float heatCoolRateScaleIn, float healthPerHardnessIn,
			float plasticToughnessIn, float glassToughnessIn, float woodToughnessIn,
			float stoneToughnessIn, float metalToughnessIn, int maxSoftBlocksIn, int maxBlocksIn,
			boolean legacyGlassIn, boolean heatBarIn)
	{
		applyConfig(enabledIn, barrelHeatIn, blockBreakingIn, heatPerShotScaleIn, heatCoolRateScaleIn,
				healthPerHardnessIn, plasticToughnessIn, glassToughnessIn, woodToughnessIn, stoneToughnessIn,
				metalToughnessIn, maxSoftBlocksIn, maxBlocksIn, legacyGlassIn, heatBarIn, DEFAULT_AMMO_HUD);
	}

	public static synchronized void applyConfig(boolean enabledIn, boolean barrelHeatIn, boolean blockBreakingIn,
			float heatPerShotScaleIn, float heatCoolRateScaleIn, float healthPerHardnessIn,
			float plasticToughnessIn, float glassToughnessIn, float woodToughnessIn,
			float stoneToughnessIn, float metalToughnessIn, int maxSoftBlocksIn, int maxBlocksIn,
			boolean legacyGlassIn, boolean heatBarIn, boolean ammoHudIn)
	{
		enabled = enabledIn;
		barrelHeat = barrelHeatIn;
		heatBar = heatBarIn;
		ammoHud = ammoHudIn;
		blockBreaking = blockBreakingIn;
		legacyGlass = legacyGlassIn;
		heatPerShotScale = heatPerShotScaleIn;
		heatCoolRateScale = heatCoolRateScaleIn;
		healthPerHardness = healthPerHardnessIn;
		plasticToughness = plasticToughnessIn;
		glassToughness = glassToughnessIn;
		woodToughness = woodToughnessIn;
		stoneToughness = stoneToughnessIn;
		metalToughness = metalToughnessIn;
		maxSoftBlocks = maxSoftBlocksIn;
		maxBlocks = maxBlocksIn;
		reapplyOverrides();
	}

	// ---------------------------------------------------------------- data pack layer

	/** Replaces the active data-pack overrides and recomputes the effective values. */
	public static synchronized void setOverrides(Map<String, Object> overrides)
	{
		OVERRIDES.clear();
		if(overrides != null)
			OVERRIDES.putAll(overrides);
		reapplyOverrides();
	}

	public static synchronized void clearOverrides()
	{
		OVERRIDES.clear();
	}

	private static void reapplyOverrides()
	{
		enabled = bool("enabled", enabled);
		barrelHeat = bool("barrelHeatEnabled", barrelHeat);
		heatBar = bool("heatBarEnabled", heatBar);
		ammoHud = bool("ammoHudEnabled", ammoHud);
		fracture = bool("fractureEnabled", fracture);
		blockBreaking = bool("blockBreakingEnabled", blockBreaking);
		legacyGlass = bool("legacyGlassBreaking", legacyGlass);
		heatPerShotScale = number("heatPerShotScale", heatPerShotScale);
		heatCoolRateScale = number("heatCoolRateScale", heatCoolRateScale);
		healthPerHardness = number("healthPerHardness", healthPerHardness);
		plasticToughness = number("plasticToughness", plasticToughness);
		glassToughness = number("glassToughness", glassToughness);
		woodToughness = number("woodToughness", woodToughness);
		stoneToughness = number("stoneToughness", stoneToughness);
		metalToughness = number("metalToughness", metalToughness);
		maxSoftBlocks = integer("maxSoftBlocksPerShot", maxSoftBlocks);
		maxBlocks = integer("maxBlocksPerShot", maxBlocks);
	}

	private static boolean bool(String key, boolean fallback)
	{
		Object value = OVERRIDES.get(key);
		if(value instanceof Boolean b)
			return b;
		if(value instanceof Number n)
			return n.doubleValue() != 0D;
		return fallback;
	}

	private static float number(String key, float fallback)
	{
		Object value = OVERRIDES.get(key);
		if(value instanceof Number n && Float.isFinite(n.floatValue()))
			return Math.max(0F, n.floatValue());
		return fallback;
	}

	private static int integer(String key, int fallback)
	{
		Object value = OVERRIDES.get(key);
		if(value instanceof Number n)
			return Math.max(1, n.intValue());
		return fallback;
	}

	// ---------------------------------------------------------------- read side

	public static boolean masterEnabled()
	{
		return enabled;
	}

	public static boolean fractureEnabled()
	{
		return enabled && fracture;
	}

	public static synchronized void setFractureEnabled(boolean value)
	{
		fracture = value;
		reapplyOverrides();
	}

	public static boolean ammoHudEnabled()
	{
		return enabled && ammoHud;
	}

	public static boolean heatBarEnabled()
	{
		return enabled && heatBar;
	}

	public static boolean barrelHeatEnabled()
	{
		return enabled && barrelHeat;
	}

	public static boolean blockBreakingEnabled()
	{
		return enabled && blockBreaking;
	}

	/** The packs' original BreaksGlass behaviour is destruction too, but has its own switch. */
	public static boolean legacyGlassBreaking()
	{
		return enabled && legacyGlass;
	}

	public static float heatPerShotScale()
	{
		return heatPerShotScale;
	}

	public static float heatCoolRateScale()
	{
		return heatCoolRateScale;
	}

	public static float healthPerHardness()
	{
		return healthPerHardness;
	}

	public static float toughness(boolean plastic, boolean glass, boolean wood, boolean metal)
	{
		if(glass)
			return glassToughness;
		if(plastic)
			return plasticToughness;
		if(metal)
			return metalToughness;
		if(wood)
			return woodToughness;
		return stoneToughness;
	}

	public static int maxSoftBlocks()
	{
		return maxSoftBlocks;
	}

	public static int maxBlocks()
	{
		return maxBlocks;
	}
}
