package com.flansmod.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.InfoType;

/**
 * Supplies the item models, icon textures and display names that content packs cannot carry.
 *
 * <p>A 1.7.10 pack describes an item's look with an {@code Icon} line, its 3D shape with a model
 * class, and its name with a {@code Name} line. Modern Minecraft instead asks the resource system
 * for {@code models/item/<name>.json}, a stitched texture and a {@code lang} entry. A pack written
 * in 2015 has none of those, so the port generates them from the parsed definitions.
 *
 * <p>Two shapes of item model come out of here, and the difference matters:
 * <ul>
 *   <li>an item whose pack ships a usable 3D model gets {@code "parent": "builtin/entity"}.
 *       That parent is what makes the baked model report {@code isCustomRenderer() == true},
 *       which is the only condition under which {@code ItemRenderer} consults the mod's
 *       {@code BlockEntityWithoutLevelRenderer} at all. The model deliberately carries no
 *       {@code display} block: 1.7.10 never consulted one, and its renderers apply the full
 *       transform themselves (see {@code TurborigItemRenderer}).</li>
 *   <li>everything else keeps {@code minecraft:item/generated} with the pack's flat icon — the
 *       picture the 1.7.10 inventory actually showed.</li>
 * </ul>
 *
 * <p>The icons deserve their own note. Packs keep them at {@code textures/items/<Name>.png} with
 * whatever case the author typed. Nothing that modern Minecraft scans will ever see those files:
 * {@code items/} is not a directory the item atlas collects, and a name with capitals cannot be
 * turned into a resource location at all. So each icon is copied into this generated pack as
 * {@code textures/item/<registry name>.png}: lower-case, in the folder the vanilla atlas reads.
 * Skins are not copied — the 3D renderer binds those directly by name, and that lookup already
 * folds case.
 */
public class GeneratedAssets extends AbstractPackResources
{
	private final Map<String, byte[]> files = new LinkedHashMap<>();
	private record Snapshot(long generation, int definitions, Map<String, byte[]> files) { }
	private static Snapshot snapshot;

	/** Used as the particle sprite when the pack ships no icon at all. */
	private static final String PARTICLE_FALLBACK = "minecraft:block/stone";

	/**
	 * The model path of an item's flat icon, for the items whose main model is a 3D rig.
	 *
	 * <p>The set of ids is kept as it is generated because the client has to register these as
	 * standalone models before they can be fetched back; the two happen at different times
	 * (this runs at mod construction, the registration during a resource reload).
	 */
	private static final java.util.Set<ResourceLocation> ICON_MODELS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Model path (no {@code models/} prefix, no extension) of an item's icon model. */
	public static String iconModelPath(String registryPath)
	{
		return "icon/" + registryPath;
	}

	/** Every registered icon model id; see {@link #iconModelPath}. */
	public static java.util.Set<ResourceLocation> iconModelIds()
	{
		return ICON_MODELS;
	}

	private GeneratedAssets(PackLocationInfo info)
	{
		super(info);
	}

	/**
	 * Writes the generated models, icons and language file to a real resource pack on disk.
	 *
	 * <p>A pack built from the definitions is served through the in-memory resource set as well,
	 * but the model bakery reads models before that set is consulted in some load orders, which
	 * shows up as every item 404ing while the pack list looks correct. Writing the same content
	 * to {@code run/flansmod_generated/} and letting the game load it as a normal pack removes the
	 * ordering question entirely: by the time anything asks for a model, the file is on disk like
	 * any other pack resource.
	 *
	 * <p>If the folder cannot be written the in-memory path is still used, so a read-only game
	 * directory degrades to "icons may not appear" rather than failing to start.
	 */
	public static void writeToDisk(Path gameDir)
	{
		try
		{
			Path root = gameDir.resolve("flansmod_generated");
			Map<String, byte[]> generated = generate();
			for(Map.Entry<String, byte[]> e : generated.entrySet())
			{
				Path file = root.resolve("assets/" + FlansMod.MODID).resolve(e.getKey());
				Files.createDirectories(file.getParent());
				Files.write(file, e.getValue());
			}

			// The pack needs a descriptor, or the repository will not list it.
			Files.writeString(root.resolve("pack.mcmeta"), """
					{
					  "pack": {
					    "pack_format": 34,
					    "description": "Flan's Mod item models and icons, generated from content pack definitions"
					  }
					}
					""", StandardCharsets.UTF_8);

			FlansMod.log("Wrote " + generated.size() + " generated asset(s) to " + root);
		}
		catch(IOException e)
		{
			FlansMod.LOGGER.warn("Could not write generated assets to disk; the in-memory pack will still serve them", e);
		}
		catch(RuntimeException e)
		{
			// A malformed icon path can make the filesystem call throw; the in-memory pack still
			// serves whatever was generated before the failure.
			FlansMod.LOGGER.warn("Generated asset writing hit a bad path", e);
		}
	}

	/** Builds the generated pack from every definition that has been registered. */
	public static GeneratedAssets build()
	{
		PackLocationInfo info = new PackLocationInfo("flans_generated",
				Component.literal("Flan's Mod generated assets"), PackSource.BUILT_IN, Optional.empty());
		GeneratedAssets assets = new GeneratedAssets(info);

		// Build from the parsed definitions directly rather than from the registry: the
		// resource list can be assembled before RegisterEvent fires, and a reload must never
		// come out empty because of that ordering.
		assets.files.putAll(wrap(generate()));
		return assets;
	}

	/** Prepends {@code assets/flansmod/} to every generated file, as a pack resource needs. */
	private static Map<String, byte[]> wrap(Map<String, byte[]> generated)
	{
		Map<String, byte[]> out = new LinkedHashMap<>();
		generated.forEach((path, data) -> out.put("assets/" + FlansMod.MODID + "/" + path, data));
		return out;
	}

	/**
	 * Produces every generated file, keyed relative to {@code assets/flansmod/}.
	 *
	 * <p>Shared by the on-disk writer and the in-memory pack so the two can never disagree.
	 */
	private static synchronized Map<String, byte[]> generate()
	{
		long generation = ContentPackIndex.current().generation();
		if(snapshot == null || snapshot.generation != generation || snapshot.definitions != InfoType.infoTypes.size())
			snapshot = new Snapshot(generation, InfoType.infoTypes.size(), generateUncached());
		return snapshot.files;
	}

	private static Map<String, byte[]> generateUncached()
	{
		ItemAssets.fillFromDefinitions();

		Map<String, InfoType> byPath = new LinkedHashMap<>();
		for(InfoType type : InfoType.infoTypes)
			byPath.putIfAbsent(ItemAssets.registryPathOf(type), type);

		Map<String, byte[]> out = new LinkedHashMap<>();
		// The display names come from the packs' own .lang files when they have them — see
		// PackLang — and the Name line is the fallback. Both langs are emitted so the game's
		// language setting picks the right one; en_us is the vanilla default and is always
		// written, exactly as before.
		PackLang.Table names = PackLang.load();
		Map<String, StringBuilder> langByLocale = new LinkedHashMap<>();
		Map<String, Boolean> firstByLocale = new LinkedHashMap<>();
		Map<String, java.util.Set<String>> written = new LinkedHashMap<>();
		for(String locale : new String[] { PackLang.EN, PackLang.ZH })
		{
			langByLocale.put(locale, new StringBuilder("{\n"));
			firstByLocale.put(locale, true);
			written.put(locale, new java.util.HashSet<>());
			// Everything the packs' own .lang files declare, verbatim — the item names under
			// "item.<ShortName>.name" and the shared vocabulary (message.*, firemode.*) that the
			// HUD and inspection messages look up. This is exactly the set of entries 1.7.10's
			// I18n had available, so a key that worked there resolves here too. Missing Chinese
			// entries fall back to en_us automatically: the game loads en_us first and overlays
			// the selected locale on top.
			for(Map.Entry<String, String> e : (PackLang.ZH.equals(locale) ? names.zh : names.en).entrySet())
			{
				appendLang(langByLocale, firstByLocale, written, locale, e.getKey(), e.getValue());
			}
		}
		int models = 0, threeD = 0, icons = 0;

		for(Map.Entry<String, String> e : ItemAssets.iconPaths().entrySet())
		{
			String path = e.getKey();
			InfoType type = byPath.get(path);

			// The icon, copied to a lower-case name in the one folder the vanilla item atlas
			// reads ("item/", singular) because the packs keep it at "items/" with mixed case.
			byte[] iconBytes = findIcon(e.getValue());
			boolean haveIcon = iconBytes != null;
			String spriteId = FlansMod.MODID + ":item/" + path;
			if(haveIcon)
			{
				out.put("textures/item/" + path + ".png", iconBytes);
				icons++;
			}

			// "Can this be drawn as its 3D model?" is decided here, once, and expressed as the
			// model's parent — because that parent is what routes the item to the custom
			// renderer at all. Two conditions: the pack must ship the model, and the kind of
			// definition must be one 1.7.10 actually drew in 3D (see InfoType.rendersInHand).
			// Class presence is sufficient to route the item. Geometry stays lazy; the renderer
			// falls back to its icon if parsing later fails or the class has no visible faces.
			boolean rigVisible = type != null && type.rendersInHand() && ModelCache.hasReference(type.modelString);

			String model;
			if(rigVisible)
			{
				model = """
						{
						  "parent": "builtin/entity",
						  "gui_light": "front",
						  "textures": { "particle": "%s" }
						}
						""".formatted(haveIcon ? spriteId : PARTICLE_FALLBACK);
				threeD++;

				// A builtin/entity model cannot also be a flat icon, but the inventory still wants
				// one — and the game only ever resolves ONE model per item (its "#inventory"
				// variant). So the icon gets its own model file, outside the item/ folder, and is
				// registered as a standalone model; the renderer looks it up by that name. See
				// ClientExtensions for the registration.
				String iconModel = """
						{
						  "parent": "minecraft:item/generated",
						  "textures": { "layer0": "%s" }
						}
						""".formatted(haveIcon ? spriteId : PARTICLE_FALLBACK);
				out.put("models/" + iconModelPath(path) + ".json", iconModel.getBytes(StandardCharsets.UTF_8));
				ICON_MODELS.add(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, iconModelPath(path)));
			}
			else
			{
				// Some definitions name an icon the pack never shipped (a handful of the class
				// and armour entries do). Pointing the model at a file that does not exist makes
				// the item render untextured and logs a warning, so the fallback is used instead
				// and the item stays legible.
				model = """
						{
						  "parent": "minecraft:item/generated",
						  "textures": { "layer0": "%s" }
						}
						""".formatted(haveIcon ? spriteId : PARTICLE_FALLBACK);
			}
			out.put("models/item/" + path + ".json", model.getBytes(StandardCharsets.UTF_8));
			models++;

			// Display name, under the SAME key 1.7.10 used: each item registered as
			// {@code <ShortName>}, so its language key is {@code item.<ShortName>.name}. The pack
			// entry (emitted above) wins where it exists; this fills the gaps with the definition's
			// own Name line so no item can ever show a raw key. See ItemFlan.getDescriptionId.
			String key = ItemAssets.packTranslationKey(type, path);
			String fallback = ItemAssets.nameOf(path) != null ? ItemAssets.nameOf(path).getString() : path;
			appendLang(langByLocale, firstByLocale, written, PackLang.EN, key,
					nameFor(names, PackLang.EN, path, type, fallback));
			appendLang(langByLocale, firstByLocale, written, PackLang.ZH, key,
					nameFor(names, PackLang.ZH, path, type, fallback));
		}
		for(String locale : langByLocale.keySet())
		{
			StringBuilder lang = langByLocale.get(locale).append("\n}\n");
			out.put("lang/" + locale + ".json", lang.toString().getBytes(StandardCharsets.UTF_8));
		}

		FlansMod.log("Generated " + models + " item model(s) (" + threeD + " with 3D rigs), "
				+ icons + " icon texture(s) and a language file");
		return out;
	}

	/**
	 * One locale's name for a definition: the pack's {@code item.<ShortName>.name} entry, else the
	 * given fallback (the {@code Name} line, else the registry path).
	 *
	 * <p>The lookup keys on the {@code ShortName} rather than the registry path because that is
	 * what {@code ItemGun.registerIcons} used in 1.7.10, and the pack files were written against
	 * it — the two differ only in case for every item in the EFT and Octagon packs.
	 */
	private static String nameFor(PackLang.Table names, String locale, String path, InfoType type, String fallback)
	{
		String fromPack = names.itemName(locale, type == null ? null : type.shortName);
		if(fromPack != null)
			return fromPack;
		if(PackLang.ZH.equals(locale))
		{
			// No Chinese entry: use the English one, so a Chinese player sees the pack's own name
			// rather than the raw display name, which may just be the ShortName.
			String english = names.itemName(PackLang.EN, type == null ? null : type.shortName);
			if(english != null)
				return english;
		}
		return fallback;
	}

	/** Appends one entry to a locale's JSON, comma-handling the first entry itself. */
	private static void appendLang(java.util.Map<String, StringBuilder> langs, java.util.Map<String, Boolean> firsts,
								   java.util.Map<String, java.util.Set<String>> written,
								   String locale, String key, String value)
	{
		if(!written.get(locale).add(key))
			return;                       // a pack entry and a definition would both claim this key
		StringBuilder lang = langs.get(locale);
		if(!firsts.get(locale))
			lang.append(",\n");
		firsts.put(locale, false);
		lang.append("  ").append(quote(key)).append(": ").append(quote(value));
	}

	/**
	 * Finds a pack's icon by the {@code Icon} path a definition names.
	 *
	 * <p>The lookup is case-insensitive because the definitions and the files can disagree on
	 * case ("items/20mmAmmo" vs {@code 20mmAmmo.png}), and it is folded here rather than at
	 * request time because this is a copy operation, not a resource lookup.
	 */
	private static byte[] findIcon(String iconPathLower)
	{
		String wanted = "assets/" + FlansMod.MODID + "/textures/" + iconPathLower + ".png";
		var entry = ContentPackIndex.current().find(wanted, true);
		if(entry.isEmpty()) return null;
		try
		{
			return entry.get().readAllBytes();
		}
		catch(IOException failure) { return null; }
	}


	/** JSON strings must escape quotes, slashes and control characters in pack translations. */
	private static String quote(String value)
	{
		return new com.google.gson.JsonPrimitive(value).toString();
	}

	@Override
	public IoSupplier<InputStream> getRootResource(String... path)
	{
		// The pack has no pack.mcmeta of its own; its metadata is supplied by the Pack that
		// owns this resource set. Returning null here (rather than throwing) is what the
		// resource manager expects for "this pack simply has no root resource".
		return null;
	}

	@Override
	public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location)
	{
		if(type != PackType.CLIENT_RESOURCES || !location.getNamespace().equals(FlansMod.MODID))
			return null;
		byte[] data = files.get("assets/" + FlansMod.MODID + "/" + location.getPath());
		if(data == null)
			return null;
		return () -> new java.io.ByteArrayInputStream(data);
	}

	@Override
	public void listResources(PackType type, String namespace, String path, ResourceOutput output)
	{
		if(type != PackType.CLIENT_RESOURCES || !namespace.equals(FlansMod.MODID))
			return;
		String prefix = "assets/" + namespace + "/" + path;
		if(!prefix.endsWith("/"))
			prefix = prefix + "/";
		for(Map.Entry<String, byte[]> e : files.entrySet())
		{
			if(!e.getKey().startsWith(prefix))
				continue;
			// The id must be the full path relative to the namespace root, not relative to the
			// queried directory — that is what every lister in the game expects.
			String rel = e.getKey().substring(("assets/" + namespace + "/").length());
			byte[] data = e.getValue();
			output.accept(ResourceLocation.fromNamespaceAndPath(namespace, rel),
					() -> new java.io.ByteArrayInputStream(data));
		}
	}

	@Override
	public Set<String> getNamespaces(PackType type)
	{
		return type == PackType.CLIENT_RESOURCES ? Set.of(FlansMod.MODID) : Set.of();
	}

	@Override
	public void close() { }
}
