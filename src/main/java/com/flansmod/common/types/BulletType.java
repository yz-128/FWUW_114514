package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

import com.flansmod.common.FlansMod;

/**
 * A bullet / shell / rocket definition, parsed from a pack's {@code bullets/*.txt}.
 *
 * <p>Ported from 1.7.10's {@code ShootableType} + {@code BulletType} pair. The old code split
 * them because grenades shared the base; the port keeps the split so a bullet and a grenade
 * can diverge later without unpicking a merged class.
 */
public class BulletType extends ShootableType
{
	public int flak = 0;
	public String flakParticles = "largesmoke";
	public boolean setEntitiesOnFire = false;

	/**
	 * Whether this round may break the blocks it hits. On by default (the mechanic is on for every
	 * bullet); the block power still depends on the round's penetration level and calibre, so a
	 * pistol round can open dirt and glass while a rifle round takes stone and a heavy calibre takes
	 * iron. Packs can opt a round out with {@code PenetratesBlocks false}.
	 */
	public boolean breaksBlocks = true;

	/**
	 * Rounds this bullet can pass through before it stops, spent one per thing hit.
	 *
	 * <p>The packs set it two ways: {@code Penetrates True/False} is the old boolean spelling
	 * (worth 1 or a quarter), and {@code Penetration}/{@code PenetratingPower} give a number
	 * directly. Both are accepted, which is what 1.7.10 did.
	 */
	public float penetratingPower = 1F;

	/** The impact sound's definition name, played where the round lands. */
	public String hitSound;
	/** How far the impact sound carries, in blocks; the packs' own default. */
	public float hitSoundRange = 15F;

	/**
	 * Whether the round is drawn full-bright.
	 *
	 * <p>Tracers and glowing rounds set this so they stay visible at night; it is the flag the
	 * original read in {@code getBrightnessForRender}.
	 */
	public boolean hasLight = false;

	/** Status effects applied to whatever the round hits, as {@code (effect, duration, level)}. */
	public final List<HitEffect> hitEffects = new ArrayList<>();

	/** One {@code PotionEffect} line: a mob effect applied on hit. */
	public record HitEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
							int duration, int amplifier) { }

	/** What this round can lock onto. */
	public boolean lockOnToPlanes = false, lockOnToVehicles = false, lockOnToMechas = false,
			lockOnToPlayers = false, lockOnToLivings = false;
	public float maxLockOnAngle = 45F;
	public float lockOnForce = 1F;

	/** Every bullet read so far, for gun ammo resolution. */
	public static final List<BulletType> bullets = new ArrayList<>();

	// ---------------------------------------------------------------- the FMUM fields
	//
	// Read and kept; the firing side consumes them one system at a time.

	/** What the round is, for the launcher's behaviour: a shell, a bomb, or a guided missile. */
	public enum WeaponType
	{
		BULLET, SHELL, BOMB, MISSILE;

		public static WeaponType get(String s)
		{
			for(WeaponType t : values())
				if(t.name().equalsIgnoreCase(s))
					return t;
			return BULLET;
		}
	}

	public WeaponType weaponType = WeaponType.BULLET;

	/** Air and water drag, 0..1, applied per tick while the round flies. */
	public float dragInAir = 0F, dragInWater = 0F;

	/** Rounds this bullet spawns on impact or launch (cluster munitions). */
	public int numBullets = 1;
	public float bulletSpreadMultiplier=1F,aimingBulletSpreadMultiplier=1F;

	/** Impact particles: how many, of what, and the smoke cloud's size and lifetime. */
	public int explodeParticles = 0, smokeTime = 0;
	public String explodeParticleType = "largesmoke", smokeParticleType = "largesmoke";
	public float smokeRadius = 0F;
	/** Potion clouds a smoke round leaves behind, in the same shape as {@link #hitEffects}. */
	public final List<HitEffect> smokeEffects = new ArrayList<>();

	/** Proximity fuses: how close something must come, and what the round does then. */
	public float livingProximityTrigger = 0F, driveableProximityTrigger = 0F, damageToTriggerer = 0F;
	public int primeDelay = 0;

	/** Guidance state the FMUM launchers drive. */
	public boolean VLS = false;
	public int VLSTime = 0;
	public boolean fixedDirection = false;
	public float turnRadius = 3F, trackPhaseSpeed = 2F, trackPhaseTurn = 0.2F;
	public String boostPhaseParticle;
	public boolean torpedo = false;
	public float maxDegreeOfLockOnMissile = 0F;
	public int tickStartHoming = 0;
	public boolean enableSACLOS = false;
	public int maxRangeOfMissile = 0, maxRange = 0;
	public boolean canSpotEntityDriveable = false, shootForSettingPos = false;
	public int shootForSettingPosHeight = 0;
	public boolean isDoTopAttack = false, manualGuidance = false;
	public int lockOnFuse = 0;

	/** Keeps a drag value inside the range FMUM clamps to. */
	private static float clamp01(float v)
	{
		return v < 0F ? 0F : v > 1F ? 1F : v;
	}

	public BulletType(TypeFile file)
	{
		super(file);
		// 1.7.10's BulletType constructor set this, so every round that does not name a skin of
		// its own draws the stock "defaultBullet" texture that the packs ship. Without it a
		// bullet with no Texture line resolves to no texture at all and renders as the missing
		// model marker.
		texture = "defaultBullet";
		bullets.add(this);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "Flak" -> flak = toInt(split[1]);
				// FlakParticles is a number in the FMUM packs and a particle name in 1.7.10's.
				// The two readings cannot be told apart by the key, only by the value, so the
				// value decides: a number is the count, anything else is the particle.
				case "FlakParticles" -> {
					if(isNumeric(split[1]))
						flak = toInt(split[1]);
					else
						flakParticles = split[1];
				}
				case "FlakParticleType" -> flakParticles = split[1];
				case "SetEntitiesOnFire" -> setEntitiesOnFire = toBool(split[1]);
				case "HitSound" -> hitSound = split[1];
				case "HitSoundRange" -> hitSoundRange = toFloat(split[1]);
				// Both spellings of penetration: the old boolean (1 round through, or a quarter)
				// and the direct number. 1.7.10 accepted either and so does the port.
				case "PenetratesBlocks" -> breaksBlocks = toBool(split[1]);
				case "Penetrates" -> penetratingPower = toBool(split[1]) ? 1F : 0.25F;
				case "Penetration", "PenetratingPower" -> penetratingPower = toFloat(split[1]);
				case "HasLight" -> hasLight = toBool(split[1]);
				case "PotionEffect" -> {
					// (effect, duration, amplifier), matching 1.7.10's getPotionEffect.
					//
					// The packs give the effect as a NUMBER — the line above it is a comment
					// listing the old ids — because that is what 1.7.10 wanted. Modern Minecraft
					// has no numeric ids, so the number is mapped through the same table the
					// comment documents. A name is also accepted, since a pack written for the
					// later lineage may use one.
					var effect = resolvePotionEffect(split[1]);
					if(effect == null)
						FlansMod.log("Bullet " + shortName + " names unknown PotionEffect '" + split[1] + "'");
					else
						hitEffects.add(new HitEffect(effect, toInt(split[2]), toInt(split[3])));
				}
				case "LockOnToPlanes" -> lockOnToPlanes = toBool(split[1]);
				case "LockOnToVehicles" -> lockOnToVehicles = toBool(split[1]);
				case "LockOnToMechas" -> lockOnToMechas = toBool(split[1]);
				case "LockOnToPlayers" -> lockOnToPlayers = toBool(split[1]);
				case "LockOnToLivings" -> lockOnToLivings = toBool(split[1]);
				case "MaxLockOnAngle" -> maxLockOnAngle = toFloat(split[1]);

				// ------------------------------------------------------------ FMUM keys
				//
				case "Bomb" -> weaponType = WeaponType.BOMB;
				case "Shell" -> weaponType = WeaponType.SHELL;
				case "Missile" -> weaponType = WeaponType.MISSILE;
				case "WeaponType" -> weaponType = WeaponType.get(split[1]);

				// Physics the FMUM rounds tune separately for air and water; FMUM clamps both
				// into 0..1 so a pack typo cannot make a round accelerate.
				case "DragInAir" -> dragInAir = clamp01(toFloat(split[1]));
				case "DragInWater" -> dragInWater = clamp01(toFloat(split[1]));

				// Effects on impact: the particle sets, and the potion cloud a smoke round leaves.
				case "NumExplodeParticles" -> explodeParticles = toInt(split[1]);
				case "ExplodeParticles" -> explodeParticleType = split[1];
				case "SmokeTime" -> smokeTime = toInt(split[1]);
				case "SmokeParticles" -> smokeParticleType = split[1];
				case "SmokeEffect" -> {
					if(split.length >= 4)
					{
						var effect = resolvePotionEffect(split[1]);
						if(effect != null)
							smokeEffects.add(new HitEffect(effect, toInt(split[2]), toInt(split[3])));
					}
				}
				case "SmokeRadius" -> smokeRadius = toFloat(split[1]);

				// Proximity fuses and their trigger damage.
				case "LivingProximityTrigger" -> livingProximityTrigger = toFloat(split[1]);
				case "VehicleProximityTrigger" -> driveableProximityTrigger = toFloat(split[1]);
				case "DamageToTriggerer" -> damageToTriggerer = toFloat(split[1]);
				case "PrimeDelay", "TriggerDelay" -> primeDelay = toInt(split[1]);

				// Guidance. FMUM's own names for the same behaviours differ on the older packs
				// (VLS / VLSTime), and both are accepted.
				case "VLS", "HasDeadZone" -> VLS = toBool(split[1]);
				case "VLSTime", "DeadZoneTime" -> VLSTime = toInt(split[1]);
				case "FixedTrackDirection" -> fixedDirection = toBool(split[1]);
				case "GuidedTurnRadius" -> turnRadius = toFloat(split[1]);
				case "GuidedPhaseSpeed" -> trackPhaseSpeed = toFloat(split[1]);
				case "GuidedPhaseTurnSpeed" -> trackPhaseTurn = toFloat(split[1]);
				case "BoostParticle" -> boostPhaseParticle = split[1];
				case "Torpedo" -> torpedo = toBool(split[1]);

				case "LockOnToDriveables" -> lockOnToPlanes = lockOnToVehicles = lockOnToMechas = toBool(split[1]);
				case "TurningForce", "LockOnForce" -> lockOnForce = toFloat(split[1]);
				case "MaxDegreeOfLockOnMissile" -> maxDegreeOfLockOnMissile = toFloat(split[1]);
				case "TickStartHoming" -> tickStartHoming = toInt(split[1]);
				case "EnableSACLOS" -> enableSACLOS = toBool(split[1]);
				case "MaxRangeOfMissile" -> maxRangeOfMissile = toInt(split[1]);
				case "MaxRange" -> maxRange = toInt(split[1]);
				case "CanSpotEntityDriveable" -> canSpotEntityDriveable = toBool(split[1]);
				case "ShootForSettingPos" -> shootForSettingPos = toBool(split[1]);
				case "ShootForSettingPosHeight" -> shootForSettingPosHeight = toInt(split[1]);
				case "IsDoTopAttack" -> isDoTopAttack = toBool(split[1]);
				case "ManualGuidance" -> manualGuidance = toBool(split[1]);
				case "LockOnFuse" -> lockOnFuse = toInt(split[1]);

				// The round count a bullet itself spawns (cluster rounds).
				case "NumBullets" -> numBullets = toInt(split[1]);
				case "SpreadMultiplier" -> {
					aimingBulletSpreadMultiplier=toFloat(split[1]);
					if(split.length>2) bulletSpreadMultiplier=toFloat(split[2]);
				}
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading bullet file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	/**
	 * The 1.7.10 potion-id table, which the packs' own comment lines document.
	 *
	 * <p>Only the ids the packs actually use are listed; the rest of the old table has no
	 * unambiguous modern equivalent. A value that is not a number is treated as a name and looked
	 * up in the registry, so a pack written for the later lineage still works.
	 */
	private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> resolvePotionEffect(String token)
	{
		// Registry access throws when the game's registry bootstrap has not run — which happens
		// in offline tooling that parses packs without starting Minecraft. The pack file is still
		// readable in that setting, so the lookup degrades to "no effect" instead of taking the
		// whole definition down with it.
		final net.minecraft.core.Registry<net.minecraft.world.effect.MobEffect> mobEffects;
		try
		{
			mobEffects = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT;
		}
		catch(Throwable registryUnavailable)
		{
			return null;
		}

		Integer legacyId = null;
		try { legacyId = Integer.parseInt(token.trim()); }
		catch(NumberFormatException ignored) { }

		String path = switch(legacyId == null ? -1 : legacyId)
		{
			case 1 -> "speed";
			case 2 -> "slowness";
			case 3 -> "haste";
			case 4 -> "mining_fatigue";
			case 5 -> "strength";
			case 6 -> "instant_health";
			case 7 -> "instant_damage";
			case 8 -> "jump_boost";
			case 9 -> "nausea";            // 1.7.10's "Confusion"
			case 10 -> "regeneration";
			case 11 -> "resistance";
			case 12 -> "fire_resistance";
			case 13 -> "water_breathing";
			case 14 -> "invisibility";
			case 15 -> "blindness";
			case 16 -> "night_vision";
			case 17 -> "hunger";
			case 18 -> "weakness";
			case 19 -> "poison";
			case 20 -> "wither";
			default -> token.trim().toLowerCase(java.util.Locale.ROOT);
		};

		// A name that already carries a namespace is left alone; a bare one is a vanilla effect,
		// which is what every pack's table lists.
		ResourceLocation id = ResourceLocation.tryParse(
				path.contains(":") ? path : "minecraft:" + path);
		var effect = id == null ? null : mobEffects.get(id);
		return effect == null ? null : mobEffects.wrapAsHolder(effect);
	}

	public static BulletType getBullet(String shortName)
	{
		for(BulletType b : bullets)
			if(b.shortName != null && b.shortName.equalsIgnoreCase(shortName))
				return b;
		return null;
	}
}
