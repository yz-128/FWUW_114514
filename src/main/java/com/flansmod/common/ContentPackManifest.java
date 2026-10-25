package com.flansmod.common;

import dev.packarms.core.IndexedPack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small, stable description of the content packs a side has loaded.
 *
 * <p>Multiplayer already requires both sides to agree on the packs: item ids, attachment names and
 * model paths all come from them. The manifest is exchanged on join so a mismatch is reported as a
 * clear list instead of appearing later as missing textures or "unknown item". Only the pack's
 * file layout is hashed (path names, count), not the bytes of every model, so joining stays cheap.
 */
public final class ContentPackManifest
{
	public record Entry(String name,int files,String hash)
	{
		public Entry normalized() {return new Entry(name,files,hash.toLowerCase(Locale.ROOT));}
	}

	private ContentPackManifest() { }

	/** The local packs, in load order, with a deterministic layout hash per pack. */
	public static List<Entry> local()
	{
		var result=new ArrayList<Entry>();
		var indexed=ContentPackIndex.current();
		for(var pair : FlansMod.contentPacks.entrySet())
		{
			String name=pair.getKey();
			IndexedPack pack=null;
			try {pack=indexed.pack(pair.getValue()).orElse(null);} catch(RuntimeException ignored) { }
			List<String> paths=new ArrayList<>();
			if(pack!=null)
				for(var entry : pack.entries()) paths.add(entry.path());
			paths.sort(Comparator.naturalOrder());
			result.add(new Entry(name,paths.size(),hash(name,paths)));
		}
		result.sort(Comparator.comparing(Entry::name));
		return List.copyOf(result);
	}

	/** Human-readable differences, empty when both sides load the same packs. */
	public static List<String> compare(List<Entry> local,List<Entry> remote)
	{
		Map<String,Entry> mine=byName(local), theirs=byName(remote);
		var differences=new ArrayList<String>();
		for(var entry : mine.values())
		{
			var other=theirs.get(entry.name());
			if(other==null) differences.add("only on this side: "+entry.name());
			else if(!entry.hash().equalsIgnoreCase(other.hash()) || entry.files()!=other.files())
				differences.add("different contents: "+entry.name());
		}
		for(var entry : theirs.values())
			if(!mine.containsKey(entry.name())) differences.add("only on the other side: "+entry.name());
		return List.copyOf(differences);
	}

	private static Map<String,Entry> byName(List<Entry> entries)
	{
		var result=new LinkedHashMap<String,Entry>();
		for(var entry : entries) result.put(entry.name(),entry.normalized());
		return result;
	}

	private static String hash(String name,List<String> paths)
	{
		try
		{
			var digest=MessageDigest.getInstance("SHA-256");
			digest.update(name.getBytes(StandardCharsets.UTF_8));
			for(var path : paths)
			{
				digest.update((byte)0);
				digest.update(path.getBytes(StandardCharsets.UTF_8));
			}
			var bytes=digest.digest();
			var text=new StringBuilder(16);
			for(int i=0;i<8;i++) text.append(String.format(Locale.ROOT,"%02x",bytes[i]));
			return text.toString();
		}
		catch(Exception error)
		{
			return "error";
		}
	}
}
