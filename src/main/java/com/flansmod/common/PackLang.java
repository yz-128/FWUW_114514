package com.flansmod.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The content packs' own language files, read the way 1.7.10 read them.
 *
 * <p>Every pack ships {@code assets/flansmod/lang/en_US.lang} and usually a {@code zh_CN.lang}
 * beside it, in the old {@code key=value} format. 1.7.10's {@code I18n} loaded them as ordinary
 * resource-pack languages, which is where the item names came from: {@code ItemGun} registered
 * itself under the pack's {@code ShortName}, so the display name was {@code item.<ShortName>.name}
 * — English from {@code en_US.lang}, Chinese from {@code zh_CN.lang} when the player switched
 * language. The packs themselves carry no other kind of name; the {@code Name} line in a
 * definition is only 1.7.10's fallback and the pack authors kept the two in step.
 *
 * <p>Modern Minecraft only reads {@code lang/<code>.json}, so those files are invisible to it
 * and every item would fall back to the {@code Name} line — English-only. This class parses the
 * old files and hands the entries to the generated pack's JSON langs; see
 * {@link GeneratedAssets#generate}.
 *
 * <p>Both pack shapes are served (loose folder, zip/jar, either wrapped one folder deep),
 * mirroring the icon lookup in {@code GeneratedAssets}. The file names are matched
 * case-insensitively because packs write both {@code zh_CN.lang} and {@code zh_cn.lang}.
 * Lines are split on the FIRST {@code =} only: pack files contain values with an {@code =} in
 * them (and at least one corrupted line that merged two entries), and splitting on the last
 * separator would lose those values entirely.
 *
 * <p>First pack wins, matching {@code ContentRegistry}: when two packs define the same item,
 * the earlier pack's item is the one that gets registered, so the earlier pack's translation is
 * the one that must show.
 */
public final class PackLang
{
	private PackLang() { }

	/** Locale codes this class reads, in the order the generated langs are written. */
	public static final String EN = "en_us";
	public static final String ZH = "zh_cn";

	/** One parsed entry file per locale, plus lower-cased indexes for name lookups. */
	public static final class Table
	{
		public final Map<String, String> en = new LinkedHashMap<>();
		public final Map<String, String> zh = new LinkedHashMap<>();
		private final Map<String, String> enLower = new LinkedHashMap<>();
		private final Map<String, String> zhLower = new LinkedHashMap<>();

		/** The value for {@code key}, exact case first, then case-insensitively. */
		public String en(String key)
		{
			String hit = en.get(key);
			return hit != null ? hit : enLower.get(key.toLowerCase(Locale.ROOT));
		}

		/** The Chinese value for {@code key}, exact case first, then case-insensitively. */
		public String zh(String key)
		{
			String hit = zh.get(key);
			return hit != null ? hit : zhLower.get(key.toLowerCase(Locale.ROOT));
		}

		/** The {@code item.<ShortName>.name} value for a definition, or null. */
		public String itemName(String locale, String shortName)
		{
			if(shortName == null || shortName.isBlank())
				return null;
			String key = "item." + shortName + ".name";
			return ZH.equals(locale) ? zh(key) : en(key);
		}

		private void index()
		{
			for(Map.Entry<String, String> e : en.entrySet())
				enLower.putIfAbsent(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
			for(Map.Entry<String, String> e : zh.entrySet())
				zhLower.putIfAbsent(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
		}
	}

	/**
	 * Pack typos repaired at load, locale -> key -> value.
	 *
	 * <p>The Octagon pack's Chinese file has a corrupted line where {@code message.fullammo=满}
	 * and the following entry were merged ({@code message.fullaitem.FASTHelmet.name=mmo=满}), so
	 * the four guns that name {@code message.fullammo} as their ammo state would print the raw key
	 * on screen. The intended value is unambiguous — the same file translates the four neighbouring
	 * states as 空/少/约半/多 — so it is restored here instead of editing the user's pack. The
	 * English side is missing the same key for the same reason.
	 */
	private static final Map<String, Map<String, String>> REPAIRS = Map.of(
			ZH, Map.of("message.fullammo", "满"),
			EN, Map.of("message.fullammo", "Full"));

	/** Reads every content pack's language files; never null, possibly empty. */
	public static Table load()
	{
		Table table = new Table();
		for(var pack : ContentPackIndex.current().packs())
		{
			try
			{
                boolean folder = !pack.archive();
				String prefix = "assets/flansmod/lang/";
				for(var entry : folder ? pack.under(prefix) : pack.entries())
				{
					if(folder && entry.path().substring(prefix.length()).contains("/")) continue;
					String locale = localeOf(entry.path().toLowerCase(Locale.ROOT));
					if(locale != null) parse(table, locale, entry.readAllBytes());
				}
			}
			catch(Exception e)
			{
				FlansMod.LOGGER.warn("Could not read the language files of " + pack.name(), e);
			}
		}
		for(Map.Entry<String, Map<String, String>> locale : REPAIRS.entrySet())
		{
			Map<String, String> target = ZH.equals(locale.getKey()) ? table.zh : table.en;
			for(Map.Entry<String, String> e : locale.getValue().entrySet())
				target.putIfAbsent(e.getKey(), e.getValue());
		}
		table.index();
		return table;
	}


	/**
	 * A language file's locale: accepts either the bare file name ({@code en_us.lang}, as the
	 * folder walk produces) or the full resource path with any case.
	 */
	private static String localeOf(String path)
	{
		if(!path.endsWith(".lang"))
			return null;
		String lower = path.toLowerCase(Locale.ROOT);
		String file = lower.substring(lower.lastIndexOf('/') + 1);
		if(file.equals("en_us.lang"))
			return EN;
		if(file.equals("zh_cn.lang"))
			return ZH;
		return null;
	}

	/**
	 * Parses one {@code key=value} file into a locale's map. Blank lines, comments ({@code #} and
	 * {@code //}, which is how these packs write section headers) and lines without a separator
	 * are skipped; the first entry for a key wins, so a pack loaded earlier keeps its names.
	 */
	private static void parse(Table table, String locale, byte[] bytes)
	{
		Map<String, String> target = ZH.equals(locale) ? table.zh : table.en;
		String text = new String(bytes, StandardCharsets.UTF_8);
		if(text.startsWith("\uFEFF"))
			text = text.substring(1);
		for(String line : text.split("\r?\n"))
		{
			String trimmed = line.trim();
			if(trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//"))
				continue;
			int eq = trimmed.indexOf('=');
			if(eq <= 0)
				continue;
			String key = trimmed.substring(0, eq).trim();
			String value = trimmed.substring(eq + 1).trim();
			if(!key.isEmpty() && !value.isEmpty())
				target.putIfAbsent(key, value);
		}
	}
}
