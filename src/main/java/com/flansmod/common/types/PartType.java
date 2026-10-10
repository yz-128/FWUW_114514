package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/**
 * A part definition, from a pack's {@code parts/*.txt}. Ported from 1.7.10's {@code PartType}.
 *
 * <p>Parts are what go into the workbench recipes and drive the vehicle engine/fuel model,
 * so the category and fuel fields matter even before driving is implemented.
 */
public class PartType extends InfoType
{
	/** 1.7.10's category ids, by name. */
	public static final int CATEGORY_FUEL = 9;

	public int category;
	public int stackSize = 1;

	public float engineSpeed = 1.0F;
	public float fuelConsumption = 1.0F;
	public int fuel = 0;

	public final List<EnumType> worksWith = new ArrayList<>(List.of(EnumType.mecha, EnumType.plane, EnumType.vehicle));

	public static final List<PartType> parts = new ArrayList<>();

	public PartType(TypeFile file)
	{
		super(file);
		parts.add(this);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "Category" -> category = getCategory(split[1]);
				case "StackSize" -> stackSize = toInt(split[1]);
				case "EngineSpeed" -> engineSpeed = toFloat(split[1]);
				case "FuelConsumption" -> fuelConsumption = toFloat(split[1]);
				case "Fuel" -> fuel = toInt(split[1]);
				case "WorksWith" -> {
					worksWith.clear();
					for(int i = 1; i < split.length; i++)
					{
						EnumType t = EnumType.get(split[i].toLowerCase(java.util.Locale.ROOT));
						if(t != null)
							worksWith.add(t);
					}
				}
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading part file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	/** The pack writes a category name; 1.7.10 mapped it to a number. */
	private static int getCategory(String name)
	{
		return switch(name.toLowerCase(java.util.Locale.ROOT))
		{
			case "fuel" -> 9;
			case "engine" -> 0;
			case "propeller" -> 1;
			case "wheels", "wheel" -> 2;
			case "track" -> 3;
			case "seat" -> 4;
			case "body", "structural" -> 5;
			case "gun", "barrel" -> 6;
			default -> 8;
		};
	}

	public static PartType getPart(String shortName)
	{
		for(PartType p : parts)
			if(p.shortName != null && p.shortName.equalsIgnoreCase(shortName))
				return p;
		return null;
	}
}
