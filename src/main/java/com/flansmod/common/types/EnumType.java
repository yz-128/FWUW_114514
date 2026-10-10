package com.flansmod.common.types;

/**
 * Every kind of content a pack can define, and the folder it lives in.
 *
 * <p>Ported from 1.7.10. The folder names are load-bearing: they are exactly what the
 * old packs on disk use, so keeping them means existing content packs need no changes.
 *
 * <p>{@link #getTypeClass()} is filled in as each type class is ported; anything not yet
 * ported falls back to the base {@link InfoType} so the parser still records the file and
 * can report it, rather than silently dropping content.
 */
public enum EnumType
{
	part("parts"),
	bullet("bullets"),
	attachment("attachments"),
	grenade("grenades"),
	gun("guns"),
	aa("aaguns"),
	vehicle("vehicles"),
	plane("planes"),
	mechaItem("mechaItems"),
	mecha("mechas"),
	tool("tools"),
	armour("armorFiles"),
	armourBox("armorBoxes"),
	box("boxes"),
	playerClass("classes"),
	team("teams");

	public final String folderName;

	EnumType(String s)
	{
		folderName = s;
	}

	public static EnumType get(String s)
	{
		for(EnumType e : values())
			if(e.folderName.equals(s))
				return e;
		return null;
	}

	/** The class that parses files of this type. Extend the switch as types are ported. */
	public Class<? extends InfoType> getTypeClass()
	{
		return switch(this)
		{
			case bullet -> BulletType.class;
			case grenade -> GrenadeType.class;
			case gun -> GunType.class;
			case part -> PartType.class;
			case attachment -> AttachmentType.class;
			case vehicle -> VehicleType.class;
			case plane -> PlaneType.class;
			case armour -> ArmourType.class;
			case tool -> ToolType.class;
			default -> InfoType.class;
		};
	}
}
