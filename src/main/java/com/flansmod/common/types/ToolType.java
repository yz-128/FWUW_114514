package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/** A tool, from {@code tools/*.txt}. Ported from 1.7.10's {@code ToolType}. */
public class ToolType extends InfoType
{
	public static final List<ToolType> tools = new ArrayList<>();

	public boolean healPlayers = false, healDriveables = false;
	public float healAmount = 0F;
	public int toolLife = 0;
	public boolean destroyOnEmpty = true;
	public int EUPerCharge = 0;
	public boolean parachute = false;
	public boolean remote = false;
	public int foodness = 0;

	public ToolType(TypeFile file)
	{
		super(file);
		tools.add(this);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "Heal", "HealPlayers" -> healPlayers = parseBool(split[1]);
				case "Repair", "RepairVehicles", "HealDriveables" -> healDriveables = parseBool(split[1]);
				case "HealAmount", "RepairAmount" -> healAmount = toFloat(split[1]);
				case "ToolLife", "ToolUses" -> toolLife = toInt(split[1]);
				case "DestroyOnEmpty" -> destroyOnEmpty = parseBool(split[1]);
				case "EUPerCharge" -> EUPerCharge = toInt(split[1]);
				case "Parachute" -> parachute = parseBool(split[1]);
				case "Remote" -> remote = parseBool(split[1]);
				case "Food", "Foodness" -> foodness = toInt(split[1]);
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading tool file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	private static boolean parseBool(String s)
	{
		return Boolean.parseBoolean(s.toLowerCase(java.util.Locale.ROOT));
	}

	public static ToolType getTool(String shortName)
	{
		for(ToolType t : tools)
			if(t.shortName != null && t.shortName.equalsIgnoreCase(shortName))
				return t;
		return null;
	}
}
