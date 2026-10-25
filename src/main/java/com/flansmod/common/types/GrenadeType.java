package com.flansmod.common.types;

import com.flansmod.common.FlansMod;

/**
 * A grenade definition, from {@code grenades/*.txt}. Ported from 1.7.10's {@code GrenadeType}
 * and extended with the FMUM keys, which is what the EFT packs write.
 */
public class GrenadeType extends ShootableType
{
	/** How long the throw animation takes, in ticks. */
	public int throwDelay = 0;
	public String throwSound;
	/** Whether the item can be thrown at all, or only placed. */
	public boolean canThrow = true;
	public String dropItemOnThrow;

	/** Bouncing and what the grenade passes through. */
	public float bounciness = 0F;
	public boolean penetratesEntities = false, penetratesBlocks = false;
	public String bounceSound;

	/** Sticking: to the thrower, to whatever it hits, and whether it announces the stick. */
	public boolean sticky = false, stickToThrower = false, stickToEntity = false,
			stickToDriveable = false, stickToEntityAfter = false;
	public boolean allowStickSound = false;
	public int stickSoundRange = 0;
	public String stickSound;

	/** Detonation conditions and their delays. */
	public boolean detonateWhenShot = false;
	public boolean remote = false;

	/** Proximity fuses: how close something must come, and the delay before the charge arms. */
	public float livingProximityTrigger = 0F, driveableProximityTrigger = 0F, damageToTriggerer = 0F;
	public int primeDelay = 0;

	/** Damage dealt to whatever the thrown grenade strikes on the way. */
	public float hitEntityDamage = 0F;

	/** Smoke and flash effects, and the crowd-control variants. */
	public String smokeEffect;
	public float smokeRadius = 0F;
	/** How long the smoke keeps applying its effects, in ticks; 1.7.10's {@code SmokeTime}. */
	public int smokeTime = 0;
	/** {@code SmokeEffect <id> <duration> <amplifier>}, in the 1.7.10 numeric potion ids. */
	public record SmokePotion(int id,int duration,int amplifier) { }
	public final java.util.List<SmokePotion> smokePotions = new java.util.ArrayList<>();
	/** Whether the thrown grenade tumbles in flight. */
	public boolean spinWhenThrown = false;
	public boolean flashBang = false;
	public int flashTime = 0;
	public float flashRange = 0F;
	public boolean flashSoundEnable = true, flashDamageEnable = false;
	public String flashSound;
	public int flashSoundRange = 0, flashDamage = 0;

	/** Medical and utility grenades: how much they heal, and how many uses they carry. */
	public float healAmount = 0F;
	public int numUses = 0, numClips = 0;

	/** Motion sensors, which arm when something walks past. */
	public boolean motionSensor = false;
	public float motionSensorRange = 0F;
	public String motionSound;
	public int motionSoundRange = 0, motionTime = 0;

	/** The deployable bag variant some utility grenades have. */
	public boolean deployableBag = false;

	/** Potion effects the grenade leaves behind, in the same shape as a bullet's hit effects. */
	public final java.util.List<BulletType.HitEffect> potionEffects = new java.util.ArrayList<>();

	public GrenadeType(TypeFile file)
	{
		super(file);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "ThrowDelay" -> throwDelay = toInt(split[1]);
				case "ThrowSound" -> throwSound = split[1];
				case "DropItemOnThrow" -> dropItemOnThrow = split[1];
				case "CanThrow" -> canThrow = toBool(split[1]);
				case "Bounciness" -> bounciness = toFloat(split[1]);
				case "PenetratesEntities" -> penetratesEntities = toBool(split[1]);
				case "PenetratesBlocks" -> penetratesBlocks = toBool(split[1]);
				case "BounceSound" -> bounceSound = split[1];
				case "Sticky" -> sticky = toBool(split[1]);
				case "StickToThrower" -> stickToThrower = toBool(split[1]);
				case "StickToEntity" -> stickToEntity = toBool(split[1]);
				case "StickToDriveable" -> stickToDriveable = toBool(split[1]);
				case "StickToEntityAfter" -> stickToEntityAfter = toBool(split[1]);
				case "AllowStickSound" -> allowStickSound = toBool(split[1]);
				case "StickSoundRange" -> stickSoundRange = toInt(split[1]);
				case "StickSound" -> stickSound = split[1];
				case "DetonateWhenShot" -> detonateWhenShot = toBool(split[1]);
				case "Remote" -> remote = toBool(split[1]);
				case "LivingProximityTrigger" -> livingProximityTrigger = toFloat(split[1]);
				case "VehicleProximityTrigger" -> driveableProximityTrigger = toFloat(split[1]);
				case "DamageToTriggerer" -> damageToTriggerer = toFloat(split[1]);
				case "PrimeDelay", "TriggerDelay" -> primeDelay = toInt(split[1]);
				case "HitEntityDamage" -> hitEntityDamage = toFloat(split[1]);

				case "SmokeEffect" ->
				{
					// FMUM: SmokeEffect <id> <duration> <amplifier>; the packs still use 1.7.10 ids.
					if(split.length >= 4) smokePotions.add(new SmokePotion(toInt(split[1]),toInt(split[2]),toInt(split[3])));
					else smokeEffect = split[1];
				}
				case "SmokeTime" -> smokeTime = toInt(split[1]);
				case "SmokeRadius" -> smokeRadius = toFloat(split[1]);

				case "FlashBang" -> flashBang = toBool(split[1]);
				case "FlashTime" -> flashTime = toInt(split[1]);
				case "FlashRange" -> flashRange = toFloat(split[1]);
				case "FlashSoundEnable" -> flashSoundEnable = toBool(split[1]);
				case "FlashSound" -> flashSound = split[1];
				case "FlashSoundRange" -> flashSoundRange = toInt(split[1]);
				case "FlashDamageEnable" -> flashDamageEnable = toBool(split[1]);
				case "FlashDamage" -> flashDamage = toInt(split[1]);
				case "FlashEffects" -> { /* accepted; effect ids are read from the two keys below */ }
				case "FlashEffectsID", "FlashEffectsDuration", "FlashEffectsLevel" ->
				{ /* accepted; the three-token flash effect is a later port */ }

				case "HealAmount" -> healAmount = toFloat(split[1]);
				case "NumUses" -> numUses = toInt(split[1]);
				case "NumClips" -> numClips = toInt(split[1]);

				case "MotionSensor" -> motionSensor = toBool(split[1]);
				case "MotionSensorRange" -> motionSensorRange = toFloat(split[1]);
				case "MotionSound" -> motionSound = split[1];
				case "MotionSoundRange" -> motionSoundRange = toInt(split[1]);
				case "MotionTime" -> motionTime = toInt(split[1]);

				case "DeployableBag" -> deployableBag = toBool(split[1]);
				case "SpinWhenThrown" -> spinWhenThrown = toBool(split[1]);
				case "AddPotionEffect" -> { /* accepted; the packed potion id is a later port */ }
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading grenade file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}
}
