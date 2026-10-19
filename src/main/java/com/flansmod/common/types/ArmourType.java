package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/**
 * Armour, from {@code armorFiles/*.txt}. Ported from 1.7.10's {@code ArmourType} and extended
 * with the FMUM keys, which is what the EFT packs are written for.
 *
 * <p>FMUM turned armour from a flat damage-reduction number into a plate model: each body part
 * has a proof level, the material it is made of, and a set of hit sounds, and the plates have
 * durability that wears down. All of that is read here.
 */
public class ArmourType extends InfoType
{
	public static final List<ArmourType> armours = new ArrayList<>();

	/** Equipment slot: 0 = head, 1 = chest, 2 = legs, 3 = feet, matching 1.7.10's ids. */
	public int type;
	public double defence;
	public String armourTextureName;

	public float moveSpeedModifier = 1F, knockbackModifier = 0.2F, jumpModifier = 1F;
	public boolean nightVision = false;
	public String overlay = null;
	public boolean smokeProtection = false;
	public boolean negateFallDamage = false;

	// ---------------------------------------------------------------- the FMUM plate model

	/**
	 * Proof level per body part, in FMUM's order:
	 * {@code 0 = head, 1 = body, 2 = left arm, 3 = right arm, 4 = mask}.
	 */
	public final float[] proofLevel = new float[5];
	/** The material named on each proof line, e.g. {@code bodyarmor}. */
	public final String[] proofMaterial = new String[5];
	/** How many hit sounds each part has, and how far they carry. */
	public final String[] hitSounds = new String[8];
	public final int[] numHitSounds = new int[8];
	public final float[] hitSoundRanges = {8F, 8F, 8F, 8F, 8F, 8F, 8F, 8F};

	/** How long the plates last, and the multipliers on wear from blunt and penetrating hits. */
	public int durability = 0;
	public boolean breakable = true;
	public float bluntDamageMult = 1F, bluntDurabilityMult = 1F;
	public float penetratedDamageMult = 1F, penetratedDurabilityMult = 1F;

	/** The mask's own toggles: sound, and whether the armour counts as one. */
	public boolean hasMask = false;
	public final String[] maskSwitchSound = new String[2];
	public float maskSwitchSoundRange = 0F;

	/** Movement and damage modifiers the later packs add. */
	public boolean invisible = false, fireResistance = false, waterBreathing = false,
			onWaterWalking = false;

	public ArmourType(TypeFile file)
	{
		super(file);
		armours.add(this);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "Type" -> type = slotId(split[1]);
				case "Defence", "Defense", "DamageReduction" -> defence = Double.parseDouble(split[1]);
				case "ArmourTextureName", "ArmorTextureName", "ArmourTexture", "ArmorTexture" ->
						armourTextureName = split[1];
				case "MoveSpeedModifier", "Slowness" -> moveSpeedModifier = toFloat(split[1]);
				case "KnockbackModifier", "KnockbackReduction" -> knockbackModifier = toFloat(split[1]);
				case "JumpModifier" -> jumpModifier = toFloat(split[1]);
				case "NightVision" -> nightVision = toBool(split[1]);
				case "Overlay" -> {
					if(!split[1].equals("None"))
						overlay = split[1];
					if(split.length > 2)
						overlaySecondary = split[2];
				}
				case "SmokeProtection" -> smokeProtection = toBool(split[1]);
				case "NegateFallDamage" -> negateFallDamage = toBool(split[1]);
				case "Invisible" -> invisible = toBool(split[1]);
				case "FireResistance" -> fireResistance = toBool(split[1]);
				case "WaterBreathing" -> waterBreathing = toBool(split[1]);
				case "OnWaterWalking" -> onWaterWalking = toBool(split[1]);

				// ------------------------------------------------------------ FMUM keys

				// Per-part proof. Each line is "level [material] [count] [range] [sound count range]":
				// the second sound set is the penetrated-hit variant, which is why the indices jump.
				case "HeadProofLevel" -> readProof(split, 0);
				case "BodyProofLevel" -> readProof(split, 1);
				case "LeftArmProofLevel" -> readProof(split, 2);
				case "RightArmProofLevel" -> readProof(split, 3);

				// The mask is a proof part too, and the only one that is a separate key.
				case "HasMask" -> {
					hasMask = toBool(split[1]);
					if(split.length > 2)
						proofLevel[4] = toFloat(split[2]);
				}

				case "Durability" -> durability = toInt(split[1]);
				case "BreakableArmor", "BreakableArmour" -> breakable = toBool(split[1]);
				case "BluntDamageMult" -> {
					bluntDamageMult = toFloat(split[1]);
					if(split.length > 2)
						bluntDurabilityMult = toFloat(split[2]);
				}
				case "PenetratedDamageMult" -> {
					penetratedDamageMult = toFloat(split[1]);
					if(split.length > 2)
						penetratedDurabilityMult = toFloat(split[2]);
				}

				case "SwitchSound" -> {
					maskSwitchSound[0] = split[1];
					if(split.length > 2)
					{
						maskSwitchSoundRange = toFloat(split[2]);
						if(split.length > 3)
							maskSwitchSound[1] = split[3];
					}
				}
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading armour file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	/** The second overlay layer, when the pack gives one. */
	public String overlaySecondary;

	/**
	 * One part's proof line.
	 *
	 * <p>Shape: {@code <level> [material] [soundCount] [range] [material2 count2 range2]} — the
	 * second triple is the penetrated-hit sound set, and the sound names are built as
	 * {@code material_0 .. material_n}, which is why only the material name is kept.
	 */
	private void readProof(String[] split, int part)
	{
		proofLevel[part] = toFloat(split[1]);
		if(split.length > 2)
		{
			proofMaterial[part] = split[2];
			if(split.length > 3)
			{
				numHitSounds[part] = toInt(split[3]);
				if(split.length > 4)
				{
					hitSoundRanges[part] = toFloat(split[4]);
					if(split.length > 5)
					{
						hitSounds[part + 4] = split[5];      // head/body/arms, then their penetrated variants
						if(split.length > 6)
							numHitSounds[part + 4] = toInt(split[6]);
						if(split.length > 7)
							hitSoundRanges[part + 4] = toFloat(split[7]);
					}
				}
			}
			else
			{
				numHitSounds[part] = 1;
			}
		}
		hitSounds[part] = proofMaterial[part] != null ? proofMaterial[part] : hitSounds[part];
	}

	/**
	 * The packs name the slot; 1.7.10 also accepted the numbers its own UI used.
	 * The 1.20 lineage renamed some of these (Boots → Feet, Body → Chest), and both
	 * spellings are accepted here so neither era of pack breaks.
	 */
	static int slotId(String name)
	{
		return switch(name.toLowerCase(java.util.Locale.ROOT))
		{
			case "helmet", "head", "hat" -> 0;
			case "chest", "body", "chestplate" -> 1;
			case "legs", "pants", "leggings" -> 2;
			case "boots", "feet", "shoes" -> 3;
			default -> {
				try { yield Integer.parseInt(name); }
				catch(NumberFormatException e) { yield 1; }
			}
		};
	}

	public static ArmourType getArmourType(String shortName)
	{
		for(ArmourType a : armours)
			if(a.shortName != null && a.shortName.equalsIgnoreCase(shortName))
				return a;
		return null;
	}
}
