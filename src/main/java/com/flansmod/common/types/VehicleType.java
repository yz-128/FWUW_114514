package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/** A ground vehicle, from {@code vehicles/*.txt}. Ported from 1.7.10's {@code VehicleType}. */
public class VehicleType extends DriveableType
{
	public float turnLeftModifier = 1F, turnRightModifier = 1F;
	public boolean squashMobs = false;
	public boolean fourWheelDrive = false;
	public boolean rotateWheels = false;
	public boolean tank = false;

	public int vehicleShootDelay, vehicleShellDelay;
	public boolean hasDoor = false;

	public static final List<VehicleType> types = new ArrayList<>();

	public VehicleType(TypeFile file)
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
				case "TurnLeftSpeed" -> turnLeftModifier = toFloat(split[1]);
				case "TurnRightSpeed" -> turnRightModifier = toFloat(split[1]);
				case "SquashMobs" -> squashMobs = parseBool(split[1]);
				case "FourWheelDrive" -> fourWheelDrive = parseBool(split[1]);
				case "RotateWheels" -> rotateWheels = parseBool(split[1]);
				case "Tank" -> tank = parseBool(split[1]);
				case "VehicleShootDelay" -> vehicleShootDelay = toInt(split[1]);
				case "VehicleShellDelay" -> vehicleShellDelay = toInt(split[1]);
				case "HasDoor" -> hasDoor = parseBool(split[1]);
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading vehicle file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	public static VehicleType getVehicle(String shortName)
	{
		for(VehicleType v : types)
			if(v.shortName != null && v.shortName.equalsIgnoreCase(shortName))
				return v;
		return null;
	}
}
