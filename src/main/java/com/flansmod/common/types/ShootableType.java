package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/**
 * Base for anything a gun can fire: bullets, shells, grenades.
 * Ported from 1.7.10's {@code ShootableType}.
 */
public class ShootableType extends InfoType
{
	public boolean trailParticles = false;
	public String trailParticleType = "smoke";

	public int maxStackSize = 1;
	public String dropItemOnReload = null, dropItemOnShoot = null, dropItemOnHit = null;
	public int roundsPerItem = 1;

	public float fallSpeed = 1.0F;
	public float throwSpeed = 1.0F;
	public float hitBoxSize = 0.5F;

	public float damageVsLiving = 1F;
	public int damageVsDriveable = 1;
	public boolean breaksGlass = false;

	// ---------------------------------------------------------------- the FMUM damage model
	//
	// FMUM split the single damage number into four targets and let a pack set any of them.
	// A value of -1 means "not set", and the pack's own fallback rules apply at read time:
	// player falls back to living, planes to vehicles (FMUM's ShootableType constructor did
	// exactly that). Keeping the sentinel means a bullet and a grenade that set different
	// subsets end up with the same effective values FMUM gave them.

	public float damageVsPlayer = -1F, damageVsVehicles = -1F, damageVsPlanes = -1F;

	/** Armour interaction: the round's armour-breaking power and the blunt-damage fraction. */
	public float armorBreakPower = 0F, bluntDamageMult = 0F;

	/** The penetration bracket the round sits in, and the range around it. */
	public float maxPenetrationLevel = 0F, minPenetrationLevel = 0F;

	/** Armour ignition probabilities, straight from the FMUM line of the same names. */
	public float ignoreArmorProbability = 0F, ignoreArmorDamageFactor = 0F;

	/** Impact audio: whether it plays, and the entity-hit variant's switch. */
	public boolean hitSoundEnable = true, entityHitSoundEnable = true;

	/**
	 * The round's own 3D model, which is separate from the item's {@code Model} line.
	 *
	 * <p>{@code BulletModel <class> [skin] [scale] [rotX rotY rotZ]} — the packs use this for
	 * the round in flight, while {@code Model} names the flat item model. Losing it means the
	 * bullet entity draws with no geometry at all.
	 */
	public String bulletModel, bulletTexture;
	public float bulletScale = 1F;
	public final float[] bulletRotCenter = new float[3];

	/** How much the round's damage varies between individual hits. */
	public float spreadMultiplier = 1F;

	/** Particles: flare and debris counts, and the submunition chain. */
	public int smokeParticleCount = 0, debrisParticleCount = 0;
	public boolean hasSubmunitions = false;
	public String submunition;
	public int numSubmunitions = 0, subMunitionTimer = 0;
	public float submunitionSpread = 1F;

	/** Explosion damage versus the four target kinds, when the pack distinguishes them. */
	public float explosionDamageVsLiving = 0F, explosionDamageVsPlayer = 0F,
			explosionDamageVsPlane = 0F, explosionDamageVsVehicle = 0F;

	public int fuse = 0;
	public int despawnTime = 0;
	public boolean explodeOnImpact = false;

	/** The round's ammunition family, for guns that list an ammo type rather than items. */
	public String ammoType;
	/** Whether this round is the casing a gun ejects rather than a projectile. */
	public boolean isCase = false;

	public float fireRadius = 0F;
	public float explosionRadius = 0F;
	public boolean explosionBreaksBlocks = false;
	public String dropItemOnDetonate = null;
	public String detonateSound = "";

	public ShootableType(TypeFile file)
	{
		super(file);
	}

	@Override
	protected void postRead(TypeFile file)
	{
		super.postRead(file);
		// FMUM's fallback rules, applied once the whole file has been read: a round that names
		// no player damage uses the living value, and one that names no plane damage uses the
		// vehicle value. These are the defaults the packs were authored against.
		if(damageVsPlayer < 0F)
			damageVsPlayer = damageVsLiving;
		if(damageVsPlanes < 0F)
			damageVsPlanes = damageVsVehicles;
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				// The packs spell the trail both ways; SmokeTrail is the older one and is how
				// almost every round in the wild turns its trail on.
				case "TrailParticles", "SmokeTrail" -> trailParticles = toBool(split[1]);
				case "TrailParticleType" -> trailParticleType = split[1];
				// Stack size: 1.7.10 called it MaxStackSize, the FMUM packs write StackSize.
				case "MaxStackSize", "StackSize" -> maxStackSize = toInt(split[1]);
				case "DropItemOnReload" -> dropItemOnReload = split[1];
				case "DropItemOnShoot" -> dropItemOnShoot = split[1];
				case "DropItemOnHit" -> dropItemOnHit = split[1];
				case "RoundsPerItem" -> roundsPerItem = toInt(split[1]);
				case "FallSpeed" -> fallSpeed = toFloat(split[1]);
				case "ThrowSpeed", "ShootSpeed" -> throwSpeed = toFloat(split[1]);
				case "HitBoxSize" -> hitBoxSize = toFloat(split[1]);
				case "DamageVsLiving" -> damageVsLiving = toFloat(split[1]);
				case "DamageVsDriveable" -> damageVsDriveable = (int) toFloat(split[1]);
				case "BreaksGlass" -> breaksGlass = toBool(split[1]);
				case "Fuse" -> fuse = toInt(split[1]);
				case "DespawnTime" -> despawnTime = toInt(split[1]);
				case "ExplodeOnImpact", "DetonateOnImpact" -> explodeOnImpact = toBool(split[1]);
				// FireRadius / ExplosionRadius are the modern spellings; the older packs write
				// Fire and Explosion, and 1.7.10 accepted either.
				case "FireRadius", "Fire" -> fireRadius = toFloat(split[1]);
				case "ExplosionRadius", "Explosion" -> explosionRadius = toFloat(split[1]);
				case "ExplosionBreaksBlocks" -> explosionBreaksBlocks = toBool(split[1]);
				case "ExplosionDamageVsLiving" -> explosionDamageVsLiving = toFloat(split[1]);
				case "ExplosionDamageVsPlayer" -> explosionDamageVsPlayer = toFloat(split[1]);
				case "ExplosionDamageVsPlane" -> explosionDamageVsPlane = toFloat(split[1]);
				case "ExplosionDamageVsVehicle" -> explosionDamageVsVehicle = toFloat(split[1]);
				case "DropItemOnDetonate" -> dropItemOnDetonate = split[1];
				case "DetonateSound" -> detonateSound = split[1];

				// ------------------------------------------------------------ FMUM keys
				// The per-target damage numbers. -1 stays "unset" so the constructor's fallback
				// rules can fill player from living and planes from vehicles afterwards.
				case "DamageVsPlayer" -> damageVsPlayer = toFloat(split[1]);
				case "DamageVsVehicles" -> damageVsVehicles = toFloat(split[1]);
				case "DamageVsPlanes" -> damageVsPlanes = toFloat(split[1]);
				// DamageVsEntity is a spelling some packs write but no core ever read; accepted
				// silently so it neither errors nor is mistaken for a value of the line above.
				case "DamageVsEntity" -> { /* accepted, unused by the reference cores */ }

				case "ArmorBreakPower" -> armorBreakPower = toFloat(split[1]);
				case "BluntDamageMult" -> bluntDamageMult = toFloat(split[1]);
				case "PenetrationLevel" -> {
					maxPenetrationLevel = toFloat(split[1]);
					minPenetrationLevel = split.length > 2 ? toFloat(split[2]) : maxPenetrationLevel;
					if(minPenetrationLevel > maxPenetrationLevel)
					{
						float smaller = maxPenetrationLevel;
						maxPenetrationLevel = minPenetrationLevel;
						minPenetrationLevel = smaller;
					}
				}
				case "I//gnoreArmorProbability", "IgnoreArmorProbability" -> ignoreArmorProbability = toFloat(split[1]);
				case "IgnoreArmorDamageFactor" -> ignoreArmorDamageFactor = toFloat(split[1]);

				case "HitSoundEnable" -> hitSoundEnable = toBool(split[1]);
				case "EntityHitSoundEnable" -> entityHitSoundEnable = toBool(split[1]);

				// The round's model, which is what draws it in flight.
				case "BulletModel" -> {
					bulletModel = split[1];
					if(split.length > 2 && !split[2].equals("None"))
						bulletTexture = split[2];
					if(split.length > 3)
						bulletScale = toFloat(split[3]);
					if(split.length > 6)
					{
						bulletRotCenter[0] = toFloat(split[4]);
						bulletRotCenter[1] = toFloat(split[5]);
						bulletRotCenter[2] = toFloat(split[6]);
					}
				}
				case "AmmoType" -> ammoType = split[1];
				// ItsCase names the casing this round leaves behind; the FMUM family stores it
				// in the same field the old DropItemOnShoot used.
				case "ItsCase" -> dropItemOnShoot = split[1];
				case "IsCase" -> isCase = toBool(split[1]);
				case "SpreadMultiplier" -> spreadMultiplier = toFloat(split[1]);

				case "FlareParticleCount" -> smokeParticleCount = toInt(split[1]);
				case "DebrisParticleCount" -> debrisParticleCount = toInt(split[1]);
				case "HasSubmunitions" -> hasSubmunitions = toBool(split[1]);
				case "Submunition" -> submunition = split[1];
				case "NumSubmunitions" -> numSubmunitions = toInt(split[1]);
				case "SubmunitionDelay" -> subMunitionTimer = toInt(split[1]);
				case "SubmunitionSpread" -> submunitionSpread = toFloat(split[1]);
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading shootable file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}
}
