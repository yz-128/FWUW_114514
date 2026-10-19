package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/**
 * Base for the definitions that can carry attachments: guns, and attachments themselves.
 *
 * <p>Mirrors the FMUM lineage's class of the same name, which sits between {@code InfoType}
 * and {@code GunType}/{@code AttachmentType} (via {@code PaintableType}). The old 1.7.10 packs
 * only used a fraction of this — a gun could name four attachment names and be done; FMUM turned
 * the same idea into geometry: every gun declares its attachment points as positions in the
 * model, and every attachment is mounted at one of those positions, optionally stepping along
 * the slot's own vector to fit a family of parts (a barrel's rail has several notches).
 *
 * <p>Everything here is parsed and kept. Placing and drawing the attachments is a later step;
 * this class exists so that the data is not thrown away while that step is written, and so a
 * pack that uses the FMUM layout parses exactly like it did in FMUM.
 */
public class ModifiableType extends InfoType
{
	/** One attachment point on the model, in blocks. */
	public static class AttachableSlot
	{
		public float x, y, z;
		/** How far one step along this slot moves a mounted part, in blocks. */
		public float stepVector;
		/** How many steps of {@link #stepVector} a position index means. */
		public int step;
		/** Extra rotation applied to whatever is mounted here, in degrees. */
		public float rotate;
		/** How many parts may be mounted on this slot at once. */
		public int maxCanAttach = 64;
	}

	public AttachableSlot[] slots = new AttachableSlot[0];

	/** Where the sights sit relative to the model origin, in blocks (FMUM divides by 16). */
	public float aimCenterY, aimCenterZ;
	/** Sight roll about the gun's X axis, in degrees; unlike Y/Z this is not a distance. */
	public float aimCenterX;

	/** The gun's collision boxes: each entry is a list of corner triples, in blocks. */
	public final List<float[][]> hitBoxes = new ArrayList<>();
	public final List<float[][]> bigHitBoxes = new ArrayList<>();

	// The four lists below are per-slot; index 0 is the first slot. A slot with nothing
	// listed is simply empty.

	/** Attachment names each slot will accept / refuse, by the attachment's own type tag. */
	public final List<List<String>> typeWhitelist = new ArrayList<>();
	public final List<List<String>> typeBlacklist = new ArrayList<>();
	public final List<List<String>> attachmentWhitelist = new ArrayList<>();
	public final List<List<String>> attachmentBlacklist = new ArrayList<>();

	/** The loadout a fresh gun comes with: per slot, a tree of parts mounted on each other. */
	public List<List<DefaultAttachment>> defaultAttachments = new ArrayList<>();
	/** Original token groups, retained because referenced attachments may be read later. */
	private final List<String[]> defaultAttachmentLines = new ArrayList<>();

	/** Parts that must be present before the gun will shoot / fire automatically / charge. */
	public final List<String> neededToShoot = new ArrayList<>(), neededToAuto = new ArrayList<>(),
			neededToCharge = new ArrayList<>();

	/** Skin variants the paintjob table offers. */
	public final List<Paintjob> paintjobs = new ArrayList<>();

	/** One paintjob: a skin name, a display colour, and the dye stacks it costs. */
	public record Paintjob(int index, String textureName, String colourName, List<String> dyes) { }

	/**
	 * One node of a {@code DefaultAttachments} tree.
	 *
	 * <p>The format is nested: an attachment may carry its own children in brackets, which is how
	 * an AK's receiver comes with its dust cover, which in turn comes with a rear sight. A node
	 * remembers the step and offset it was written with so the same tree can be rebuilt on a gun
	 * stack later exactly as the pack described it.
	 */
	public static class DefaultAttachment
	{
		public String name;
		/** Position along the owner's slot, in blocks (step count times the slot vector). */
		public float pos;
		/** Which slot of the owner this node sits in. */
		public int slot;
		public int step;
		public int offset;
		/** Per-instance optical settings; never written into a shared AttachmentType. */
		public int zoomStep, dotFrame;
		/** Paintjob index or part id, when the line carried one; null when it did not. */
		public Integer paintjob;
		/** Original item components, retained while this part is mounted. */
		public net.minecraft.nbt.CompoundTag itemData;
		public final List<DefaultAttachment> children = new ArrayList<>();
	}

	public ModifiableType(TypeFile file)
	{
		super(file);
	}

	/**
	 * Scales everything measured against the model into the units the model is drawn in.
	 *
	 * <p>This is FMUM's own post-read step and it is load-bearing: slot positions are written in
	 * the pack's pixel units divided by 16, but a model is drawn at {@code modelScale / 16} per
	 * unit. An attachment on a rifle at 0.2 therefore has to have its slot multiplied by 0.2, or
	 * every part sits five times too far from the origin. The same applies to the step vector and
	 * the sight centre.
	 */
	@Override
	protected void postRead(TypeFile file)
	{
		super.postRead(file);
		float scale = modelScale <= 0F ? 1F : modelScale;
		for(AttachableSlot slot : slots)
		{
			slot.x *= scale;
			slot.y *= scale;
			slot.z *= scale;
			slot.stepVector *= scale;
		}
		aimCenterY *= scale;
		aimCenterZ *= scale;

		// Fill in each node's position now that the slots carry their final scale: the position is
		// the slot's step vector times the step the line wrote, plus the attachment's own offset
		// entry when one was given. FMUM computed the same value when it serialised the gun.
		for(List<DefaultAttachment> group : defaultAttachments)
			for(DefaultAttachment node : group)
				applyNodePositions(node, this);
	}

	/**
	 * Resolves a node's and its children's positions along their own slots.
	 *
	 * <p>Each level looks up the slot on its OWN owner: a gun's slot positions place the parts
	 * directly on the gun, while a part's children are placed by that part's own {@code
	 * AttachPoints}. Walking down therefore has to carry the owner with it.
	 */
	private void applyNodePositions(DefaultAttachment node, ModifiableType owner)
	{
		AttachmentType at = AttachmentType.getAttachment(node.name);
		if(at != null)
		{
			if(node.slot >= 0 && node.slot < owner.slots.length)
				node.pos = owner.slots[node.slot].stepVector * node.step;
			if(node.offset >= 0 && node.offset < at.attachOffsets.length)
				node.pos += at.attachOffsets[node.offset];
		}
		for(DefaultAttachment child : node.children)
			applyNodePositions(child, at != null ? at : owner);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				// The sights' offset from the origin; FMUM keeps these in blocks so the rendering
				// side can use them without another division.
				case "AimCenter" -> {
					aimCenterY = toFloat(split[1]) / 16F;
					if(split.length > 2)
						aimCenterZ = toFloat(split[2]) / 16F;
					if(split.length > 3)
						aimCenterX = toFloat(split[3]);
				}

				// Attachment points: flat triples, one per slot. Their count decides how many
				// slots exist, which every other slot-keyed line below relies on.
				case "AttachPoints" -> {
					slots = new AttachableSlot[(split.length - 1) / 3];
					for(int i = 3, s = 0; i < split.length; i += 3, s++)
					{
						AttachableSlot slot = new AttachableSlot();
						slot.x = toFloat(split[i - 2]) / 16F;
						slot.y = toFloat(split[i - 1]) / 16F;
						slot.z = toFloat(split[i]) / 16F;
						slots[s] = slot;
					}
				}
				case "AttachVectors" -> {
					if(slots.length == 0)
						break;                     // a file may set this before the points; ignore
					for(int i = 2, s = 0; i < split.length && s < slots.length; i += 2, s++)
					{
						slots[s].stepVector = toFloat(split[i - 1]) / 16F;
						slots[s].step = toInt(split[i]);
					}
				}
				case "AttachRotates" -> {
					if(slots.length == 0)
						break;
					for(int i = 1, s = 0; i < split.length && s < slots.length; i++, s++)
						slots[s].rotate = toFloat(split[i]);
				}
				case "MaxAttachments" -> {
					if(slots.length == 0)
						break;
					for(int i = 1, s = 0; i < split.length && s < slots.length; i++, s++)
						slots[s].maxCanAttach = toInt(split[i]);
				}

				case "HitBox" -> parseHitBoxes(split, hitBoxes);
				case "BigHitBox" -> parseHitBoxes(split, bigHitBoxes);

				case "NeededToShoot" -> addAll(neededToShoot, split, 1);
				case "NeededToAuto" -> addAll(neededToAuto, split, 1);
				case "NeededToCharge" -> addAll(neededToCharge, split, 1);

				// The four permission lists share one format: entries are grouped per slot and the
				// word "next" starts the next slot, so an empty group is two "next" in a row.
				case "AttachmentWhitelist" -> parseSlotList(split, attachmentWhitelist);
				case "AttachmentBlacklist" -> parseSlotList(split, attachmentBlacklist);
				case "TypeWhitelist" -> parseSlotList(split, typeWhitelist);
				case "TypeBlacklist" -> parseSlotList(split, typeBlacklist);

				case "DefaultAttachments" -> {
					defaultAttachmentLines.add(split.clone());
					parseDefaultAttachments(split);
				}

				// A paintjob is a named skin: the texture to draw with and the dye that buys it.
				// Kept verbatim; the paintjob table block itself is a later port.
				case "Paintjob" -> {
					List<String> dyes = new ArrayList<>();
					// split[3] onwards alternates dye name and count.
					for(int i = 3; i + 1 < split.length; i += 2)
						dyes.add(split[i] + " " + split[i + 1]);
					paintjobs.add(new Paintjob(paintjobs.size(), split[1],
							split.length > 2 ? split[2] : "", dyes));
				}
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading attachment-capable file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	/** Appends every token from {@code from} on to a list. */
	protected static void addAll(List<String> list, String[] split, int from)
	{
		for(int i = from; i < split.length; i++)
			list.add(split[i]);
	}

	/**
	 * Reads one of the per-slot name lists.
	 *
	 * <p>The format is {@code name name next name next next name}: groups separated by the word
	 * {@code "next"}, one group per slot. A group is kept as its own list so slot N's entries can
	 * be compared against what is mounted there, and a missing group stays an empty list rather
	 * than shifting every later slot's contents up by one.
	 */
	private static void parseSlotList(String[] split, List<List<String>> dest)
	{
		List<String> group = new ArrayList<>();
		for(int i = 1; i < split.length; i++)
		{
			if(split[i].equals("next"))
			{
				dest.add(group);
				group = new ArrayList<>();
			}
			else
			{
				group.add(split[i]);
			}
		}
		dest.add(group);
	}

	/**
	 * Parses the {@code DefaultAttachments} tree.
	 *
	 * <p>Shape: groups separated by {@code "next"} (one group per slot), and within a group a
	 * space-separated run of attachments. An attachment may carry children in square brackets,
	 * and a {@code "|"} between siblings marks the start of a new root in the same group. After a
	 * name an optional integer is the slot step, and an optional further integer is the attach
	 * offset index — exactly the three states FMUM's reader cycles through.
	 */
	private void parseDefaultAttachments(String[] split)
	{
		int[] at = { 1 };
		int[] budget = { Math.max(0, com.flansmod.common.GunAttachments.MAX_NODES
				- com.flansmod.common.GunAttachments.count(defaultAttachments)) };
		int slot = 0;
		List<DefaultAttachment> group = new ArrayList<>();
		while(at[0] < split.length)
		{
			String token = split[at[0]];
			if(token.equals("next"))
			{
				defaultAttachments.add(group);
				group = new ArrayList<>();
				slot++;
				at[0]++;
			}
			else if(token.equals("|"))
			{
				at[0]++;
			}
			else
			{
				DefaultAttachment node = parseDefaultNode(split, at, slot, 0, budget);
				if(node != null)
					group.add(node);
			}
		}
		defaultAttachments.add(group);
	}

	/**
	 * Call once all content definitions are registered. Rebuilds only authored default trees;
	 * never re-reads/scales the definition and never touches saved per-item custom assemblies.
	 * References are not implicitly expanded into the referenced type's own default tree, so
	 * circular names cannot recurse; explicit bracket trees are depth/node bounded below.
	 */
	public void resolveDefaultAttachments()
	{
		if(defaultAttachmentLines.isEmpty()) return;
		defaultAttachments = new ArrayList<>();
		for(String[] line : defaultAttachmentLines) parseDefaultAttachments(line);
		for(List<DefaultAttachment> group : defaultAttachments)
			for(DefaultAttachment node : group) applyNodePositions(node, this);
	}

	/** One attachment node plus, recursively, anything it mounts. */
	private DefaultAttachment parseDefaultNode(String[] split, int[] at, int slot, int depth, int[] budget)
	{
		if(at[0] >= split.length)
			return null;

		DefaultAttachment node = new DefaultAttachment();
		node.name = split[at[0]++];
		node.slot = slot;
		// Missing names are retried after ALL definitions load, then genuinely missing names drop.
		if(AttachmentType.getAttachment(node.name) == null || depth >= com.flansmod.common.GunAttachments.MAX_DEPTH || budget[0] <= 0)
		{
			skipNodeBody(split, at);
			return null;
		}
		budget[0]--;

		// Up to three values may follow a name, and FMUM reads them in this order: the slot step,
		// the attach-offset index, and a paintjob (a number, or a part's short name). Anything
		// else — the next name, a bracket, "next" — ends the list.
		if(at[0] < split.length && isInt(split[at[0]]))
			node.step = toInt(split[at[0]++]);
		if(at[0] < split.length && isInt(split[at[0]]))
			node.offset = toInt(split[at[0]++]);
		if(at[0] < split.length && isInt(split[at[0]]))
			node.paintjob = toInt(split[at[0]++]);
		else if(at[0] < split.length && !split[at[0]].equals("[") && !split[at[0]].equals("next")
				&& !split[at[0]].equals("|") && !split[at[0]].equals("]"))
			node.paintjob = split[at[0]++].hashCode();       // a named variant (a part's shortName)

		if(at[0] < split.length && split[at[0]].equals("["))
		{
			// Children live in the bracket, and "next" inside it walks the CHILD's own slots —
			// not the parent's, which is what makes an AK's dust cover carry its own rear sight.
			at[0]++;
			int childSlot = 0;
			while(at[0] < split.length && !split[at[0]].equals("]"))
			{
				if(split[at[0]].equals("next"))
				{
					childSlot++;
					at[0]++;
					continue;
				}
				if(split[at[0]].equals("|"))
				{
					at[0]++;
					continue;
				}
				DefaultAttachment child = parseDefaultNode(split, at, childSlot, depth + 1, budget);
				if(child != null)
					node.children.add(child);
			}
			if(at[0] < split.length)
				at[0]++;                                    // consume the "]"
		}
		return node;
	}

	/** Moves past a rejected node's bracketed body so its children are not read as siblings. */
	private static void skipNodeBody(String[] split, int[] at)
	{
		// Optional numbers belong to the rejected node and must be skipped too.
		int guard = 0;
		while(at[0] < split.length && isInt(split[at[0]]) && guard++ < 3)
			at[0]++;
		if(at[0] < split.length && split[at[0]].equals("["))
		{
			int depth = 0;
			while(at[0] < split.length)
			{
				if(split[at[0]].equals("["))
					depth++;
				else if(split[at[0]].equals("]"))
				{
					depth--;
					if(depth == 0)
					{
						at[0]++;
						return;
					}
				}
				at[0]++;
			}
		}
	}

	private static boolean isInt(String s)
	{
		if(s.isEmpty())
			return false;
		for(int i = 0; i < s.length(); i++)
			if(!Character.isDigit(s.charAt(i)) && s.charAt(i) != '-')
				return false;
		return true;
	}

	/**
	 * Reads a hit box: bracketed groups of {@code (x,y,z)} corners, eight per box.
	 * The parentheses and commas are part of the token, as the packs write them.
	 */
	private static void parseHitBoxes(String[] split, List<float[][]> dest)
	{
		List<float[]> corners = new ArrayList<>();
		for(int i = 1; i < split.length; i++)
		{
			String token = split[i];
			if(token.equals("["))
			{
				corners = new ArrayList<>();
			}
			else if(token.equals("]"))
			{
				if(!corners.isEmpty())
					dest.add(corners.toArray(new float[0][]));
			}
			else if(token.startsWith("("))
			{
				String body = token.substring(1, token.endsWith(")") ? token.length() - 1 : token.length());
				String[] parts = body.split(",");
				if(parts.length >= 3)
					corners.add(new float[] {
							toFloat(parts[0]) / 16F, toFloat(parts[1]) / 16F, toFloat(parts[2]) / 16F });
			}
		}
	}
}
