package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * One {@code .txt} file from a content pack, split into lines and made readable.
 *
 * <p>Ported straight from the 1.7.10 class of the same name. Fidelity note: this is the
 * whole reason the port reads the old packs directly — the definition format never
 * changed in the content packs, so the parsers are carried over instead of being
 * re-invented as JSON (which is where the 1.20.1 lineage introduced its drift).
 */
public class TypeFile
{
	public EnumType type;
	public String name;
	public ArrayList<String> lines;
	public static HashMap<EnumType, ArrayList<TypeFile>> files;
	/** Which content pack this file came from; the renderer resolves skins relative to it. */
	public String contentPack = "";
	private int readerPosition = 0;

	static
	{
		files = new HashMap<>();
		for(EnumType type : EnumType.values())
			files.put(type, new ArrayList<>());
	}

	public TypeFile(EnumType t, String s)
	{
		this(t, s, true);
	}

	public TypeFile(EnumType t, String s, boolean addToTypeFileList)
	{
		type = t;
		name = s;
		lines = new ArrayList<>();
		if(addToTypeFileList)
			files.get(type).add(this);
	}

	public String readLine()
	{
		if(readerPosition == lines.size())
			return null;
		return lines.get(readerPosition++);
	}

	/** Restarts iteration; the deserialisers each walk the file once. */
	public void reset()
	{
		readerPosition = 0;
	}
}
