package com.flansmod.common;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Works out where a content pack's structure actually begins.
 *
 * <p>Content packs in the wild come in four shapes, and all four are supported:
 * <ol>
 *   <li><b>folder</b> — {@code Flan/WW2 Pack/guns/…}</li>
 *   <li><b>zip</b> — {@code Flan/pack.zip} with {@code guns/…} at the archive root</li>
 *   <li><b>jar</b> — same as zip; some packs are packaged as jars</li>
 *   <li><b>wrapped archive</b> — the whole pack sits one folder deep, e.g.
 *       {@code OctagonWeaponsReforged_1.33/guns/…}. Re-zipped uploads routinely look like
 *       this, and 1.7.10 tolerated it because it searched the tree for known folders.</li>
 * </ol>
 *
 * <p>The rule for detecting the root is the same in every case: if a known pack folder
 * ({@code guns}, {@code vehicles}, {@code assets}, …) sits at the top, the root is the top;
 * if exactly one folder contains all of them, that folder is the root.
 */
public final class PackLayout
{
	/** Folder names that only ever appear inside a content pack. */
	static final Set<String> PACK_FOLDERS = Set.of(
			"guns", "vehicles", "planes", "bullets", "attachments", "parts", "grenades",
			"tools", "boxes", "aas", "aaguns", "mechas", "mechaItems", "classes", "teams",
			"armorFiles", "armorBoxes", "assets", "com");

	private PackLayout() { }

	/**
	 * @param dir a folder that may be a pack root or may wrap one
	 * @return the folder that actually holds the pack, or {@code dir} if none looks better
	 */
	public static Path findPackRoot(Path dir)
	{
		if(hasPackFolder(dir, ""))
			return dir;

		try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir))
		{
			Path onlyChild = null;
			for(Path child : stream)
			{
				if(!Files.isDirectory(child))
					continue;
				if(onlyChild != null)
					return dir;                 // more than one candidate: do not guess
				onlyChild = child;
			}
			if(onlyChild != null && hasPackFolder(onlyChild, ""))
				return onlyChild;
		}
		catch(IOException ignored) { }
		return dir;
	}

	/**
	 * @param zip an opened archive
	 * @return the entry prefix the pack lives under: {@code ""} or {@code "SomeFolder/"}
	 */
	public static String findArchiveRoot(ZipFile zip)
	{
		List<String> names = zip.stream().map(ZipEntry::getName).toList();
		if(hasPackFolder(names, ""))
			return "";

		// Exactly one top-level folder? Use it.
		String onlyTop = null;
		for(String name : names)
		{
			int slash = name.indexOf('/');
			if(slash <= 0)
				continue;
			String top = name.substring(0, slash + 1);
			if(onlyTop == null)
				onlyTop = top;
			else if(!onlyTop.equals(top))
				return "";
		}
		if(onlyTop != null && hasPackFolder(names, onlyTop))
			return onlyTop;
		return "";
	}

	/** Does {@code dir} (joined with prefix) contain a known pack folder directly? */
	private static boolean hasPackFolder(Path dir, String ignored)
	{
		try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir))
		{
			for(Path child : stream)
				if(Files.isDirectory(child) && PACK_FOLDERS.contains(child.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
					return true;
		}
		catch(IOException ignored2) { }
		return false;
	}

	/** Do the entry names contain {@code prefix + <known folder>}? */
	private static boolean hasPackFolder(Collection<String> names, String prefix)
	{
		for(String name : names)
		{
			if(!name.startsWith(prefix))
				continue;
			String rest = name.substring(prefix.length());
			int slash = rest.indexOf('/');
			if(slash <= 0)
				continue;
			if(PACK_FOLDERS.contains(rest.substring(0, slash).toLowerCase(java.util.Locale.ROOT)))
				return true;
		}
		return false;
	}
}
