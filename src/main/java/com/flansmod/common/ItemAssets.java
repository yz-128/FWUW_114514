package com.flansmod.common;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.flansmod.common.types.InfoType;

/**
 * Bridges a definition to the two things modern Minecraft needs before an item can render:
 * a model and a display name.
 *
 * <p>1.7.10 needed neither as data — {@code ItemXxx.registerIcons} bound a texture directly
 * and {@code setUnlocalizedName} plus a {@code .lang} file supplied the name. 1.21 wants a
 * {@code models/item/<name>.json} and a {@code lang/en_us.json} entry, both keyed by the
 * registry name.
 *
 * <p>This class supplies the bridge lazily, at resource-resolution time, from the definition
 * itself. The model is the flat icon the pack ships ({@code textures/items/<Icon>.png}), which
 * is what the 1.7.10 inventory showed; the 3D rig is resolved separately once the model
 * loader is in place.
 */
public final class ItemAssets
{
	private ItemAssets() { }

	/** Registry path -> the icon texture path inside {@code assets/flansmod/}, or null. */
	private static final Map<String, String> ICONS = new LinkedHashMap<>();
	private static final Map<String, Component> NAMES = new LinkedHashMap<>();

	/** Records what an item should look like and be called. Called once per definition. */
	public static void register(String registryPath, InfoType type)
	{
		ICONS.put(registryPath, iconPathOf(type, registryPath));
		NAMES.put(registryPath, Component.literal(type.displayName()));
	}

	/**
	 * Fills the maps from the parsed definitions alone.
	 *
	 * <p>Used when the resource list is assembled before items are registered — the two orders
	 * are both legal, and a pack reload must never come out empty because of it.
	 */
	public static void fillFromDefinitions()
	{
		for(InfoType type : InfoType.infoTypes)
		{
			String path = registryPathOf(type);
			if(!ICONS.containsKey(path))
				register(path, type);
		}
	}

	/** The registry path an item will have, mirroring {@code ContentRegistry.registryPath}. */
	public static String registryPathOf(InfoType type)
	{
		String name = type.shortName != null && !type.shortName.isBlank()
				? type.shortName
				: (type.name != null ? type.name : "unnamed");
		return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
	}

	private static String iconPathOf(InfoType type, String registryPath)
	{
		String icon = type.iconPath;
		if(icon == null || icon.isBlank())
			icon = type.shortName != null ? type.shortName : registryPath;
		// The packs write icons either bare ("BAR") or already under a folder ("items/BAR").
		String path = icon.contains("/") ? icon : "items/" + icon;

		// Resource paths must be lower-case; the packs freely mix case ("items/20mmAmmo").
		// The old loader got away with it because it built a direct file path, whereas the
		// modern resource system validates every location and refuses the model outright.
		// Pack textures are named to match the Icon line, so the lookup is case-insensitive;
		// see ContentPackResources, which resolves the real entry name.
		return path.toLowerCase(java.util.Locale.ROOT);
	}

	/** The texture an item should draw, or null to fall back to the missing-texture marker. */
	public static ResourceLocation iconOf(String registryPath)
	{
		String path = ICONS.get(registryPath);
		return path == null ? null
				: ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, path);
	}

	public static Map<String, String> iconPaths()
	{
		return ICONS;
	}

	public static Component nameOf(String registryPath)
	{
		return NAMES.get(registryPath);
	}

	/**
	 * The language key of a definition's display name, in 1.7.10's scheme.
	 *
	 * <p>1.7.10 registered each item as {@code GameRegistry.registerItem(item, shortName)} and its
	 * {@code .lang} files answered {@code item.<ShortName>.name} — that is the key every content
	 * pack's language files were written against, so it is the one the port must look up (see
	 * PackLang). The registry path is the fallback for a definition whose ShortName is missing.
	 */
	public static String packTranslationKey(InfoType type, String registryPath)
	{
		String name = type != null && type.shortName != null && !type.shortName.isBlank()
				? type.shortName
				: registryPath;
		return "item." + name + ".name";
	}
}
