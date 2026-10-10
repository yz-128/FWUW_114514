package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/**
 * Base for vehicles and planes, from {@code vehicles/*.txt} and {@code planes/*.txt}.
 * Ported from 1.7.10's {@code DriveableType}; the fields a pack can set are all kept, since
 * losing one would make a pack silently behave differently rather than fail loudly.
 *
 * <p>Positions in these files are written in the model's own units and divided by 16 on read,
 * exactly as 1.7.10 did — that is the bridge between a pack's coordinates and world metres.
 */
public class DriveableType extends InfoType
{
	//Weapons
	public String ammoNames = null;
	public final List<String> ammoNameList = new ArrayList<>();
	public boolean acceptAllAmmo = true;
	public String primary = "NONE", secondary = "NONE";
	public boolean alternatePrimary = false, alternateSecondary = false;
	public int shootDelayPrimary = 1, shootDelaySecondary = 1;
	public GunType.EnumFireMode modePrimary = GunType.EnumFireMode.FULLAUTO;
	public GunType.EnumFireMode modeSecondary = GunType.EnumFireMode.FULLAUTO;
	public String shootSoundPrimary, shootSoundSecondary;
	public final List<float[]> shootPointsPrimary = new ArrayList<>(), shootPointsSecondary = new ArrayList<>();

	//Passengers
	public int numPassengers = 0;
	public int numPassengerGunners = 0;
	public int numCargoSlots, numBombSlots, numMissileSlots;

	//Fuel / engine
	public int fuelTankSize = 100;
	public String startSound;
	public int startSoundLength;
	public String engineSound;
	public int engineSoundLength;

	//Physics
	public float yOffset = 10F / 16F;
	public float cameraDistance = 5F;
	public float maxThrottle = 1F, maxNegativeThrottle = 0F;
	public float[] turretOrigin = new float[3];
	public final List<float[]> wheelPositions = new ArrayList<>();
	public float wheelSpringStrength = 0.5F;
	public float wheelStepHeight = 1.0F;
	public final List<float[]> collisionPoints = new ArrayList<>();

	/**
	 * The track link points, two per tank: one chain per side, in model units.
	 *
	 * <p>A tank lists these in pairs — every link along the tread — so the list is long. It is what
	 * a tracked vehicle's ground contact and tread motion are built from.
	 */
	public final List<float[]> leftLinkPoints = new ArrayList<>();
	public final List<float[]> rightLinkPoints = new ArrayList<>();
	public float drag = 1F;

	public boolean floatOnWater = false;
	public boolean placeableOnLand = true, placeableOnWater = false;
	public float buoyancy = 0.0165F;
	public float bulletDetectionRadius = 5F;

	public boolean harvestBlocks = false;

	public DriveableType(TypeFile file)
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
				//Weapons
				case "Ammo" -> { ammoNames = split[1]; ammoNameList.add(split[1]); }
				case "AcceptAllAmmo" -> acceptAllAmmo = parseBool(split[1]);
				case "Primary" -> primary = split[1];
				case "Secondary" -> secondary = split[1];
				case "AlternatePrimary" -> alternatePrimary = parseBool(split[1]);
				case "AlternateSecondary" -> alternateSecondary = parseBool(split[1]);
				case "ShootDelayPrimary" -> shootDelayPrimary = toInt(split[1]);
				case "ShootDelaySecondary" -> shootDelaySecondary = toInt(split[1]);
				case "ModePrimary" -> modePrimary = GunType.EnumFireMode.getFireMode(split[1]);
				case "ModeSecondary" -> modeSecondary = GunType.EnumFireMode.getFireMode(split[1]);
				case "ShootSoundPrimary" -> shootSoundPrimary = split[1];
				case "ShootSoundSecondary" -> shootSoundSecondary = split[1];
				case "ShootPointPrimary" -> shootPointsPrimary.add(partPos(split, 1));
				case "ShootPointSecondary" -> shootPointsSecondary.add(partPos(split, 1));

				//Passengers
				case "Passengers" -> numPassengers = toInt(split[1]);
				case "PassengerGunners" -> numPassengerGunners = toInt(split[1]);
				case "CargoSlots" -> numCargoSlots = toInt(split[1]);
				case "BombSlots" -> numBombSlots = toInt(split[1]);
				case "MissileSlots" -> numMissileSlots = toInt(split[1]);

				//Fuel / engine
				case "FuelTankSize" -> fuelTankSize = toInt(split[1]);
				case "StartSound" -> startSound = split[1];
				case "StartSoundLength" -> startSoundLength = toInt(split[1]);
				case "EngineSound" -> engineSound = split[1];
				case "EngineSoundLength" -> engineSoundLength = toInt(split[1]);

				//Physics
				case "YOffset" -> yOffset = toFloat(split[1]) / 16F;
				case "CameraDistance" -> cameraDistance = toFloat(split[1]);
				case "MaxThrottle" -> maxThrottle = toFloat(split[1]);
				case "MaxNegativeThrottle" -> maxNegativeThrottle = toFloat(split[1]);
				case "TurretOrigin" -> turretOrigin = new float[] {
						toFloat(split[1]) / 16F, toFloat(split[2]) / 16F, toFloat(split[3]) / 16F };
				case "WheelPosition" -> wheelPositions.add(partPos(split, 1));
				// The two track link points, which every tank in the packs lists once per link.
				// They are what a tracked vehicle's ground contact and tread animation are built
				// from, so losing them is why a tank has no treads and floats over rough ground.
				case "LeftLinkPoint" -> leftLinkPoints.add(partPos(split, 1));
				case "RightLinkPoint" -> rightLinkPoints.add(partPos(split, 1));
				case "WheelSpringStrength" -> wheelSpringStrength = toFloat(split[1]);
				case "WheelStepHeight" -> wheelStepHeight = toFloat(split[1]);
				case "CollisionPoint" -> collisionPoints.add(partPos(split, 1));
				case "Drag" -> drag = toFloat(split[1]);
				case "FloatOnWater" -> floatOnWater = parseBool(split[1]);
				case "PlaceableOnLand" -> placeableOnLand = parseBool(split[1]);
				case "PlaceableOnWater" -> placeableOnWater = parseBool(split[1]);
				case "Buoyancy" -> buoyancy = toFloat(split[1]);
				case "BulletDetectionRadius" -> bulletDetectionRadius = toFloat(split[1]);

				case "HarvestBlocks" -> harvestBlocks = parseBool(split[1]);
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading driveable file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	/**
	 * A driveable position in a pack file, in model units, returned as blocks.
	 *
	 * <p>The packs write these two ways and both are in use:
	 * <ul>
	 *   <li>{@code WheelPosition 0 -32 -8 -18} — the coordinates as separate tokens, with an index
	 *       in front;</li>
	 *   <li>{@code RightLinkPoint [44.5,9,-17.5]} — a bracketed, comma-separated vector.</li>
	 * </ul>
	 *
	 * <p>The bracketed form is the one that bites: a naive read takes {@code [44.5,9,-17.5]} as
	 * three tokens and {@code toFloat} turns each into zero, so every link point lands at the
	 * origin and the vehicle's collision and wheel geometry silently collapses.
	 */
	static float[] partPos(String[] split, int from)
	{
		// The bracketed form arrives as a single token; rebuild its three numbers.
		if(from < split.length && split[from].startsWith("["))
		{
			String joined = String.join(" ", java.util.Arrays.copyOfRange(split, from, split.length));
			String[] parts = joined.replace('[', ' ').replace(']', ' ')
					.replace(',', ' ').trim().split("\\s+");
			if(parts.length < 3)
				return new float[3];
			return new float[] {
					toFloat(parts[0]) / 16F, toFloat(parts[1]) / 16F, toFloat(parts[2]) / 16F };
		}

		if(split.length < from + 3)
			return new float[3];
		return new float[] {
				toFloat(split[from]) / 16F,
				toFloat(split[from + 1]) / 16F,
				toFloat(split[from + 2]) / 16F };
	}

	static boolean parseBool(String s)
	{
		return Boolean.parseBoolean(s.toLowerCase(java.util.Locale.ROOT));
	}
}
