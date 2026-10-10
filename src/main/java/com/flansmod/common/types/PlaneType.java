package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/** A plane or helicopter, from {@code planes/*.txt}. Ported from 1.7.10's {@code PlaneType}. */
public class PlaneType extends DriveableType
{
	/** PLANE / HELICOPTER / PROPELLER — the flight model the pack asks for. */
	public String mode = "PLANE";

	public float lookDownModifier = 1F, lookUpModifier = 1F;
	public float rollLeftModifier = 1F, rollRightModifier = 1F;
	public float turnLeftModifier = 1F, turnRightModifier = 1F;
	public float lift = 1F;

	public float[] bombPosition = new float[3];
	public int planeShootDelay;
	public int planeBombDelay;

	/** Propellers: {part, x, y, z} in model units, plus the heli variants. */
	public final List<float[]> propellers = new ArrayList<>();
	public final List<float[]> heliPropellers = new ArrayList<>(), heliTailPropellers = new ArrayList<>();

	public static final List<PlaneType> types = new ArrayList<>();

	public PlaneType(TypeFile file)
	{
		super(file);
		types.add(this);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "Mode" -> mode = split[1];
				case "LookDownModifier" -> lookDownModifier = toFloat(split[1]);
				case "LookUpModifier" -> lookUpModifier = toFloat(split[1]);
				case "RollLeftModifier" -> rollLeftModifier = toFloat(split[1]);
				case "RollRightModifier" -> rollRightModifier = toFloat(split[1]);
				case "TurnLeftModifier" -> turnLeftModifier = toFloat(split[1]);
				case "TurnRightModifier" -> turnRightModifier = toFloat(split[1]);
				case "Lift" -> lift = toFloat(split[1]);
				case "BombPosition" -> bombPosition = new float[] {
						toFloat(split[1]) / 16F, toFloat(split[2]) / 16F, toFloat(split[3]) / 16F };
				case "PlaneShootDelay" -> planeShootDelay = toInt(split[1]);
				case "PlaneBombDelay" -> planeBombDelay = toInt(split[1]);
				case "Propeller" -> propellers.add(partPos(split, 1));
				case "HeliPropeller" -> heliPropellers.add(partPos(split, 1));
				case "HeliTailPropeller" -> heliTailPropellers.add(partPos(split, 1));
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading plane file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	public static PlaneType getPlane(String shortName)
	{
		for(PlaneType p : types)
			if(p.shortName != null && p.shortName.equalsIgnoreCase(shortName))
				return p;
		return null;
	}
}
