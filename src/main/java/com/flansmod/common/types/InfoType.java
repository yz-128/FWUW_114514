package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.flansmod.common.FlansMod;

/**
 * Base class for everything a content pack can define: guns, bullets, vehicles, armour,
 * parts, tools. Ported from the 1.7.10 class of the same name.
 *
 * <p>The parser shape is deliberately unchanged: {@link #read(TypeFile)} walks the file and
 * hands each non-comment line to {@link #read(String[], TypeFile)}, which the subclasses
 * extend with their own keys. Keeping that structure is what lets the old {@code .txt}
 * packs load verbatim.
 */
public class InfoType
{
	/** Every definition found across all content packs. */
	public static final List<InfoType> infoTypes = new ArrayList<>();

	/** Which kind of content this is, taken from the folder it was read out of. */
	public EnumType enumType = EnumType.part;

	public String contentPack;

	/** Filled in once the item for this definition has been registered. */
	public Item item;

	public int colour = 0xffffff;
	/** Legacy numeric id from the old packs; unused on 1.21, kept so recipes still parse. */
	public int itemID;
	public String iconPath;
	public Object[] recipe;
	public String[] recipeLine;
	/**
	 * The 3×3 recipe pattern, one character per cell, taken from the lines that follow a
	 * {@code Recipe} header. 1.7.10 stored these as three raw strings and let the crafting code
	 * interpret them; keeping the characters makes the pattern usable without re-parsing.
	 */
	public final char[][] recipeGrid = new char[3][3];
	public int recipeOutput = 1;
	public boolean shapeless;
	public String smeltableFrom = null;
	public String name;
	public String shortName;
	public String texture;
	public String modelString;
	public String description;
	public float modelScale = 1F;
	/** If false, the item cannot be dropped */
	public boolean canDrop = true;

	public InfoType(TypeFile file)
	{
		contentPack = file.contentPack;
		enumType = file.type;
		infoTypes.add(this);
	}

	/** The kind of content this definition is; the registry and tabs filter on it. */
	public EnumType type()
	{
		return enumType;
	}

	public void read(TypeFile file)
	{
		preRead(file);
		for(;;)
		{
			String line = file.readLine();
			if(line == null)
				break;
			if(line.trim().startsWith("//"))
				continue;

			// The recipe grid is not one line: 1.7.10 read the three rows that FOLLOW a
			// {@code Recipe} / {@code ShapelessRecipe} line as the row data, and packs still write
			// them that way. They are indented, and often a row is a single letter with trailing
			// spaces, so they cannot be recognised by the ordinary key/value path — the key has to
			// be seen first and the rows consumed here.
			String[] split = splitConfigLine(line);
			if(split.length == 0)
				continue;
			if(split[0].equalsIgnoreCase("Recipe") || split[0].equalsIgnoreCase("ShapelessRecipe"))
			{
				readRecipeGrid(file, split);
				continue;
			}

			if(split.length < 2)
				continue;
			read(split, file);
		}
		postRead(file);
	}

	/**
	 * Consumes the three rows after a recipe header, exactly as 1.7.10 did.
	 *
	 * <p>Blank lines and comments are skipped rather than counted, which is what the original's
	 * {@code i--} did; a row may be empty (a gap in the pattern), so an empty line is only
	 * discarded when it is genuinely blank, not when it is a row of spaces. The rows are kept
	 * verbatim once trimmed of their trailing whitespace, because the pattern's meaning lives in
	 * the columns.
	 */
	private void readRecipeGrid(TypeFile file, String[] header)
	{
		for(int row = 0; row < 3; row++)
		{
			String next = file.readLine();
			if(next == null)
				return;
			if(next.trim().isEmpty() || next.trim().startsWith("//"))
			{
				row--;
				continue;
			}
			if(recipeGrid[row] == null)
				recipeGrid[row] = new char[3];
			String trimmed = next.trim();
			for(int col = 0; col < 3 && col < trimmed.length(); col++)
				recipeGrid[row][col] = trimmed.charAt(col);
		}
		recipeLine = header;
		shapeless = header[0].equalsIgnoreCase("ShapelessRecipe");
	}

	/**
	 * Splits one definition line into its key and value tokens.
	 *
	 * <p>1.7.10 split on a single space, which worked because its packs were written that way.
	 * They are not all: some indent keys, some separate with tabs, and some pad values with runs of
	 * spaces. Splitting on any whitespace run is what the 1.20.1 lineage settled on
	 * ({@code TypeFile}'s {@code \\s+} pattern) and it is a strict superset of the old behaviour —
	 * a line the old code parsed still parses the same way.
	 *
	 * <p>A trailing {@code //} comment is dropped: the packs use it inline, and without this the
	 * comment's words become extra value tokens.
	 */
	protected static String[] splitConfigLine(String line)
	{
		String trimmed = line.trim();
		if(trimmed.isEmpty())
			return EMPTY_SPLIT;

		// A bracket may contain spaces that are part of the value rather than separators, so an
		// inline comment is only cut when it is outside one. That is the same rule the 1.20.1
		// lineage's tokenizer applies.
		int comment = indexOfComment(trimmed);
		if(comment >= 0)
		{
			trimmed = trimmed.substring(0, comment).trim();
			if(trimmed.isEmpty())
				return EMPTY_SPLIT;
		}

		String[] parts = trimmed.split("\\s+");
		return parts.length == 0 ? EMPTY_SPLIT : parts;
	}

	/** Where an inline {@code //} starts, ignoring any inside a bracketed value; -1 for none. */
	private static int indexOfComment(String line)
	{
		int depth = 0;
		for(int i = 0; i + 1 < line.length(); i++)
		{
			char c = line.charAt(i);
			if(c == '[' || c == '(' || c == '{')
				depth++;
			else if(c == ']' || c == ')' || c == '}')
				depth = Math.max(0, depth - 1);
			else if(c == '/' && line.charAt(i + 1) == '/' && depth == 0)
				return i;
		}
		return -1;
	}

	private static final String[] EMPTY_SPLIT = new String[0];

	protected void preRead(TypeFile file) { }
	protected void postRead(TypeFile file) { }

	/** Subclass parser. Call {@code super.read(split, file)} first to get the common keys. */
	protected void read(String[] split, TypeFile file)
	{
		try
		{
			switch(split[0].toLowerCase())
			{
				// The Model line carries more than a class name in the FMUM lineage: the packs
				// write "Model <class> <skin> <scale>" and, on attachments, a fifth token naming
				// the coated-glass model. 1.7.10 read only the class name and took the skin from a
				// separate Texture line, so absorbing the extras is a strict superset — a pack
				// written either way ends up with the same fields set. Without this the EFT guns
				// lose their skin AND keep modelScale at 1 while the pack asked for 0.2, which is
				// a five-times oversized rifle with no texture.
				case "model" -> {
					modelString = split[1];
					if(split.length > 2 && !isNumeric(split[2]))
						texture = split[2];
					if(split.length > 3)
						modelScale = toFloat(split[3]);
				}
				case "modelscale" -> modelScale = toFloat(split[1]);
				case "name" -> name = joinFrom(split, 1);
				case "description" -> description = joinFrom(split, 1);
				case "shortname" -> shortName = split[1];
				case "colour", "color" -> colour = (toInt(split[1]) << 16)
						+ (toInt(split[2]) << 8) + toInt(split[3]);
				case "itemid" -> itemID = toInt(split[1]);
				case "icon" -> iconPath = split[1];
				// The skin a definition draws with: a bare file name under the packs'
				// assets/flansmod/skins/ folder. 1.7.10 read this key here and
				// FlansModResourceHandler.getTexture turned it into "skins/<name>.png", which is
				// exactly what the port's renderers repeat. Without it the field stays null and
				// every model draws with the missing-pack marker — which reads as a dark,
				// untextured gun rather than as an obvious error.
				case "texture" -> texture = split[1];
				case "recipeoutput" -> recipeOutput = toInt(split[1]);
				case "smeltablefrom" -> smeltableFrom = split[1];
				case "candrop" -> canDrop = toBool(split[1]);
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading file failed : " + shortName);
			e.printStackTrace();
		}
	}

	/** {@code Name Panzer VI "Tiger"} — everything after the key, re-joined with single spaces. */
	private static String joinFrom(String[] split, int from)
	{
		StringBuilder sb = new StringBuilder(split[from]);
		for(int i = from + 1; i < split.length; i++)
			sb.append(' ').append(split[i]);
		return sb.toString();
	}

	/**
	 * Reads a number the way the packs actually write them.
	 *
	 * <p>A field the mod treats as an integer is not always one on disk — packs contain
	 * {@code DamageVsLiving 6.5} and similar. 1.7.10 got away with this because its parser
	 * caught the exception and kept whatever it had; the port instead reads the value and
	 * truncates, so a pack that was usable in 1.7.10 stays usable and the number is not lost.
	 */
	protected static float toFloat(String s)
	{
		try
		{
			return Float.parseFloat(s.trim());
		}
		catch(NumberFormatException e)
		{
			return 0F;
		}
	}

	/** {@link #toFloat} for fields that really are counts. */
	protected static int toInt(String s)
	{
		return (int) toFloat(s);
	}

	/** Tolerant boolean: packs write True/true/TRUE and occasionally 1. */
	protected static boolean toBool(String s)
	{
		String t = s.trim();
		if(t.equals("1"))
			return true;
		if(t.equals("0"))
			return false;
		return Boolean.parseBoolean(t.toLowerCase(java.util.Locale.ROOT));
	}

	/** True when a token parses as a number; used to tell Model-line variants apart. */
	protected static boolean isNumeric(String s)
	{
		if(s == null || s.isEmpty())
			return false;
		try
		{
			Float.parseFloat(s);
			return true;
		}
		catch(NumberFormatException e)
		{
			return false;
		}
	}

	/**
	 * The name shown in-game. Falls back through the pack's display name to the shortName, so
	 * an item is never labelled with a raw translation key.
	 */
	public String displayName()
	{
		if(name != null && !name.isBlank())
			return name;
		if(shortName != null && !shortName.isBlank())
			return shortName;
		return "Unnamed";
	}

	/**
	 * Whether this kind of definition was drawn as its 3D model in the world.
	 *
	 * <p>1.7.10 registered an item renderer for exactly five kinds — guns, grenades, planes,
	 * vehicles and mechas (mechas are not ported yet) — so those are the only definitions whose
	 * model class was ever used to draw the item itself. Everything else (attachments, parts,
	 * bullets, tools, armour) showed its flat icon even when the pack shipped a model class,
	 * because that class only appeared once it was attached to a gun.
	 */
	public boolean rendersInHand()
	{
		return this instanceof GunType
				|| this instanceof GrenadeType
				|| this instanceof PlaneType
				|| this instanceof VehicleType;
	}

	/** Look up the definition that owns an item stack, or null. */
	public static InfoType getType(ItemStack stack)
	{
		if(stack == null || stack.isEmpty())
			return null;
		for(InfoType type : infoTypes)
			if(type.item != null && stack.getItem() == type.item)
				return type;
		return null;
	}

	/** Look up by shortName (case-insensitive), or null. */
	public static InfoType getType(String shortName)
	{
		for(InfoType type : infoTypes)
			if(type.shortName != null && type.shortName.equalsIgnoreCase(shortName))
				return type;
		return null;
	}
}
