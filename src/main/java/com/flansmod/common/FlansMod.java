package com.flansmod.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import com.flansmod.common.types.EnumType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.TypeFile;
import com.mojang.logging.LogUtils;

/**
 * Flan's Mod, ported from the 1.7.10 sources.
 *
 * <p>Content model (kept from 1.7.10 on purpose): the game reads content packs out of a
 * {@code Flan/} folder next to the game directory, either as loose folders or as
 * {@code .zip}/{@code .jar} archives. Each pack holds one folder per {@link EnumType}
 * ({@code guns/}, {@code vehicles/}, …) full of {@code .txt} definitions, plus
 * {@code assets/} with skins, sounds and icons.
 *
 * <p>What had to change for 1.21.1 is only the part that physically cannot work the same
 * way: 1.7.10 loaded each model as a {@code .class} via its own class loader, which modern
 * Minecraft does not permit. The port instead parses the same {@code .class} bytes without
 * loading them (see {@code com.flansmod.client.model.loader.BytecodeModel}), so packs still
 * need no conversion.
 */
@Mod(FlansMod.MODID)
public class FlansMod
{
	public static final String MODID = "flansmod";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static FlansMod INSTANCE;

	/** The folder content packs are read from, {@code <game>/Flan/}. */
	public static Path flanDir;

	/** Every pack we found, name -> location, for the log line and later asset resolution. */
	public static final Map<String, Path> contentPacks = new LinkedHashMap<>();

	//A standardised ticker for all bit of the mod to call upon if they need one
	public static int ticker = 0;
	public static long lastTime;

	/** Entity types the mod owns. 1.7.10 had a single one; the port keeps the same registry shape. */
	public static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.entity.EntityType<?>>
			ENTITIES = net.neoforged.neoforge.registries.DeferredRegister.create(
					net.minecraft.core.registries.Registries.ENTITY_TYPE, MODID);

	/**
	 * The fired-round entity.
	 *
	 * <p>Its render distance is deliberately generous: a bullet is a fast, small entity and the
	 * default tracking range is five chunks, which would make long shots silently stop being drawn
	 * (they still fly — the server owns those — they just stop being visible). 128 blocks is the
	 * same number the 1.20.1 build settled on.
	 */
	public static final net.neoforged.neoforge.registries.DeferredHolder<
			net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.flansmod.common.entity.EntityBullet>>
			BULLET = ENTITIES.register("bullet", () ->
					net.minecraft.world.entity.EntityType.Builder
							.<com.flansmod.common.entity.EntityBullet>of(
									com.flansmod.common.entity.EntityBullet::new,
									net.minecraft.world.entity.MobCategory.MISC)
							.sized(0.25F, 0.25F)
							.noSummon()
							.clientTrackingRange(128)
							.updateInterval(1)
							.build("bullet"));

	public FlansMod(IEventBus modEventBus, ModContainer modContainer)
	{
		INSTANCE = this;
		LOGGER.info("Preinitialising Flan's Mod (1.7.10 port).");

		flanDir = FMLPaths.GAMEDIR.get().resolve("Flan");
		if(!Files.exists(flanDir))
		{
			log("Flan folder not found. Creating empty folder.");
			log("You should get some content packs and put them in the Flan folder.");
			try { Files.createDirectories(flanDir); }
			catch(IOException e) { LOGGER.error("Could not create Flan folder", e); }
		}

		readContentPacks();

		// Report named class presence without extracting or baking every pack model. Parsing
		// failures are cached and reported when that asset is first requested.
		reportModelAvailability();

		// The item models and display names are generated from the definitions that were just
		// parsed. They go to disk before anything asks for a resource, because the model bakery
		// can run before this mod's in-memory resource set is consulted.
		GeneratedAssets.writeToDisk(FMLPaths.GAMEDIR.get());

		// Make the packs' assets/ trees visible to the game, so skins and sounds inside a
		// content pack resolve. Without this the definitions load but nothing is textured.
		registerPackRepository(modEventBus);

		ENTITIES.register(modEventBus);
	}

	/**
	 * Counts named classes present in the shared index; does not validate class geometry.
	 *
	 * <p>Missing classes fall back to flat icons. The count makes that visible in the log while
	 * keeping unused models lazy.
	 */
	private static void reportModelAvailability()
	{
		int withModel = 0, withoutClass = 0, noModelNamed = 0;
		for(com.flansmod.common.types.InfoType type : com.flansmod.common.types.InfoType.infoTypes)
		{
			String ref = type.modelString;
			if(ref == null || ref.isBlank() || ref.equalsIgnoreCase("none") || ref.equalsIgnoreCase("null"))
			{
				noModelNamed++;
				continue;
			}
		if(com.flansmod.client.model.loader.ModelCache.hasReference(ref))
				withModel++;
			else
				withoutClass++;
		}
		log("Model class index: " + withModel + " present, " + withoutClass
				+ " named but not in any pack, " + noModelNamed + " without a model field (geometry is loaded on demand)");
	}

	/**
	 * Adds the Flan folder as a resource-pack source.
	 *
	 * <p>{@code AddPackFindersEvent} is a mod-bus event (it arrives while the resource pack list
	 * is being assembled, which happens before the game bus could dispatch it), so the listener
	 * goes on the passed-in event bus rather than on {@code NeoForge.EVENT_BUS}.
	 */
	private void registerPackRepository(IEventBus modEventBus)
	{
		modEventBus.addListener((net.neoforged.neoforge.event.AddPackFindersEvent event) ->
		{
			log("AddPackFindersEvent: packType=" + event.getPackType() + " trusted=" + event.isTrusted());
			try
			{
				event.addRepositorySource(new ContentPackResources(flanDir));
			}
			catch(Throwable t)
			{
				LOGGER.error("Could not add the content pack repository source", t);
			}
		});
	}

	/**
	 * Finds every content pack under {@link #flanDir}.
	 *
	 * <p>A name beginning with {@code _} or {@code .} is skipped, for both folders and archives.
	 * That is the way to keep a pack around without loading it: drop it in a subfolder such as
	 * {@code Flan/_disabled/} and the game neither reads its definitions nor offers its assets.
	 * Without this rule a stash folder is itself scanned as a pack, which is harmless but makes
	 * the pack count misleading.
	 */
	private static List<Path> findContentPacks()
	{
		List<Path> packs = new ArrayList<>();
		try(DirectoryStream<Path> stream = Files.newDirectoryStream(flanDir))
		{
			for(Path path : stream)
			{
				if(isDisabledName(path.getFileName().toString()))
					continue;

				if(Files.isDirectory(path))
					packs.add(path);
				else
				{
					String n = path.getFileName().toString().toLowerCase();
					if(n.endsWith(".zip") || n.endsWith(".jar"))
						packs.add(path);
				}
			}
		}
		catch(IOException e)
		{
			LOGGER.error("Could not list the Flan folder", e);
		}
		return packs;
	}

	/** True for names a pack can hide behind, e.g. {@code _disabled} or {@code .backup}. */
	public static boolean isDisabledName(String name)
	{
		return name.startsWith("_") || name.startsWith(".");
	}

	/**
	 * Reads the definitions from every content pack into {@link TypeFile#files}.
	 * Mirrors 1.7.10's {@code getTypeFiles}, including the folder layout and the fact that
	 * both loose folders and archives are accepted.
	 */
	private void readContentPacks()
	{
		List<Path> packs = findContentPacks();
		if(packs.isEmpty())
		{
			log("No content packs found in " + flanDir);
			return;
		}

		for(Path pack : packs) contentPacks.put(pack.getFileName().toString(), pack);
		ContentPackIndex.current();
		for(Path pack : packs)
		{
			String packName = pack.getFileName().toString();
			contentPacks.put(packName, pack);
			try
			{
				readIndexedPack(pack, packName);
			}
			catch(Exception e)
			{
				LOGGER.error("Failed reading content pack " + packName, e);
			}
		}

		int total = 0;
		StringBuilder summary = new StringBuilder();
		for(EnumType type : EnumType.values())
		{
			int n = TypeFile.files.get(type).size();
			if(n > 0)
			{
				total += n;
				summary.append(summary.length() > 0 ? ", " : "").append(type.folderName).append('=').append(n);
			}
		}
		log("Found " + packs.size() + " content pack(s) in " + flanDir);
		log("Definitions: " + summary);

		parseDefinitions();
	}

	/** Definition order and direct-child filtering retain the legacy pack rules. */
	private void readIndexedPack(Path source, String packName) throws IOException
	{
		var indexed = ContentPackIndex.current().pack(source).orElse(null);
		if(indexed == null) return;
		for(EnumType type : EnumType.values())
		{
			String prefix = type.folderName + "/";
			for(var entry : indexed.under(prefix))
			{
				if(indexed.archive() && !entry.path().startsWith(prefix)) continue;
				String rest = entry.path().substring(prefix.length());
				if(rest.contains("/") || !isDefinitionFile(rest)) continue;
				TypeFile file = new TypeFile(type, stripExtension(rest));
				file.contentPack = packName;
				file.lines.addAll(TextDecoding.decode(entry.readAllBytes()).lines().toList());
			}
		}
	}

	/**
	 * Opens a pack archive, tolerating the encodings packs actually ship with.
	 *
	 * <p>Zip entry names are supposed to be UTF-8, but packs built on Chinese Windows often store
	 * them in GBK, which makes the JDK reject the whole central directory with
	 * "invalid CEN header". Retrying with the platform charset is what 1.7.10's loader did
	 * implicitly, and it is the only way those archives can be read at all.
	 */
	public static ZipFile openZip(Path pack) throws IOException
	{
		try
		{
			return new ZipFile(pack.toFile(), StandardCharsets.UTF_8);
		}
		catch(java.util.zip.ZipException utf8Failure)
		{
			// Not UTF-8. The JDK also refuses a charset name it does not know, so fall back in
			// steps and only then give up with the original error.
			for(String charset : new String[] { "GBK", "GB18030", "CP936", null })
			{
				try
				{
					return charset == null
							? new ZipFile(pack.toFile())
							: new ZipFile(pack.toFile(), java.nio.charset.Charset.forName(charset));
				}
				catch(IOException | RuntimeException ignored) { }
			}
			throw utf8Failure;
		}
	}

	private static String stripExtension(String name)
	{
		int i = name.lastIndexOf('.');
		return i >= 0 ? name.substring(0, i) : name;
	}

	/**
	 * Whether a folder entry is a definition file the loader should read.
	 *
	 * <p>{@code .txt} is the 1.7.10 convention and covers every original pack. The FMUM lineage
	 * additionally reads {@code .df}, and its packs use it for roughly half their attachments —
	 * an AK's barrel, dust cover, muzzle brake and stock all live in {@code .df} files, so a
	 * loader that only knows {@code .txt} loses those parts without any error. FMUM itself reads
	 * every file in the type folder regardless of extension; accepting both is the narrower
	 * version of that, and it keeps stray files like {@code pack.mcmeta} out.
	 */
	public static boolean isDefinitionFile(String name)
	{
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		return lower.endsWith(".txt") || lower.endsWith(".df");
	}

	/**
	 * Turns every collected {@link TypeFile} into a definition object.
	 *
	 * <p>Subclasses are added to {@link EnumType#getTypeClass()} as they are ported; until
	 * then a file still produces a base {@link InfoType}, so nothing silently disappears —
	 * the counts stay honest and the loader reports what it could not specialise.
	 */
	private static void parseDefinitions()
	{
		for(EnumType type : EnumType.values())
		{
			for(TypeFile file : TypeFile.files.get(type))
			{
				Class<? extends InfoType> clazz = type.getTypeClass();
				try
				{
					InfoType definition = clazz.getConstructor(TypeFile.class).newInstance(file);
					definition.contentPack = file.contentPack;
					definition.read(file);
				}
				catch(Exception e)
				{
					LOGGER.error("Failed to read " + type.folderName + "/" + file.name, e);
				}
			}
		}
		log("Parsed " + InfoType.infoTypes.size() + " definition(s)");
		linkGunAmmo();
	}

	/**
	 * Resolves every gun's {@code Ammo} names to the {@link com.flansmod.common.types.BulletType}s
	 * they name.
	 *
	 * <p>This has to happen after all files are read: a gun's file only records the ammo's short
	 * name, and the bullets are separate files that may be read before or after the gun. 1.7.10
	 * did the same lookup lazily at load time; without it {@code GunType.ammo} stays empty, the
	 * magazine system believes no round fits any gun, and loading a gun fails before it starts —
	 * which reads on screen as "the gun never reloads and never fires".
	 */
	private static void linkGunAmmo()
	{
		int links = 0, missing = 0;
		for(InfoType definition : InfoType.infoTypes)
		{
			if(!(definition instanceof com.flansmod.common.types.GunType gun))
				continue;
			for(String ammoName : gun.ammoNames)
			{
				com.flansmod.common.types.BulletType bullet =
						com.flansmod.common.types.BulletType.getBullet(ammoName);
				if(bullet == null)
				{
					missing++;
					continue;
				}
				if(!gun.ammo.contains(bullet))
				{
					gun.ammo.add(bullet);
					links++;
				}
			}
		}

		// The FMUM family does not put bullets on the gun at all: the gun names magazine
		// TYPES ("Mag AK545Magazine"), each magazine definition carries that type on its own
		// MagType line, and the magazine's Ammo line names a CALIBRE TAG rather than a bullet —
		// the individual rounds are all the bullets whose AmmoType carries that tag. (FMUM's
		// own isValidAmmo compares the two hashes, which is the same rule.)
		//
		// Resolving that chain is what lets an EFT rifle load a round with the magazine system
		// the port already has; without it every EFT gun has an empty ammo list and can never
		// chamber anything, which reads as "the gun does not work".
		//
		// Magazines are matched by their MagType tag (how the EFT packs are written — several
		// magazines share one type) and by their ShortName (how a pack naming one specific
		// magazine writes it). Rounds are matched by AmmoType first, then by name, so a pack
		// that lists bullets directly still resolves.
		int magLinks = 0;
		for(InfoType definition : InfoType.infoTypes)
		{
			if(!(definition instanceof com.flansmod.common.types.GunType gun))
				continue;
			if(gun.itsType == com.flansmod.common.types.GunType.GunItsType.MAG || gun.magazineNames.isEmpty())
				continue;
			for(String magName : gun.magazineNames)
			{
				for(com.flansmod.common.types.GunType mag : findMagazines(magName))
				{
					for(String ammoName : mag.ammoNames)
					{
						for(com.flansmod.common.types.BulletType bullet : bulletsForAmmoTag(ammoName))
						{
							if(!gun.ammo.contains(bullet))
							{
								gun.ammo.add(bullet);
								magLinks++;
							}
						}
					}
				}
			}
		}

		log("Linked " + links + " gun-ammo pair(s), " + magLinks + " via magazine type(s), "
				+ missing + " unresolved ammo name(s)");
	}

	/** Every magazine definition a gun's {@code Mag} token could refer to, by tag or shortName. */
	private static java.util.List<com.flansmod.common.types.GunType> findMagazines(String magName)
	{
		java.util.List<com.flansmod.common.types.GunType> found = new java.util.ArrayList<>();
		for(InfoType definition : InfoType.infoTypes)
		{
			if(!(definition instanceof com.flansmod.common.types.GunType gun)
					|| gun.itsType != com.flansmod.common.types.GunType.GunItsType.MAG)
				continue;
			if(gun.shortName != null && gun.shortName.equalsIgnoreCase(magName))
				found.add(gun);
			else if(gun.magazineNames.stream().anyMatch(n -> n.equalsIgnoreCase(magName)))
				found.add(gun);
		}
		return found;
	}

	/**
	 * The rounds a magazine tag stands for.
	 *
	 * <p>A tag matches a bullet's {@code AmmoType} first — that is the FMUM rule, and it is why
	 * one magazine lists "545x39" and gets every 5.45 round the pack defines. A bullet that
	 * declares no AmmoType uses its own shortName as the tag (FMUM's fallback), and a name that
	 * is a bullet's ShortName also matches, which keeps the older packs working.
	 */
	private static java.util.List<com.flansmod.common.types.BulletType> bulletsForAmmoTag(String ammoName)
	{
		java.util.List<com.flansmod.common.types.BulletType> found = new java.util.ArrayList<>();
		for(com.flansmod.common.types.BulletType bullet : com.flansmod.common.types.BulletType.bullets)
		{
			if(bullet.shortName != null && bullet.shortName.equalsIgnoreCase(ammoName))
				found.add(bullet);
			else if(bullet.ammoType != null && bullet.ammoType.equalsIgnoreCase(ammoName))
				found.add(bullet);
		}
		return found;
	}

	public static void log(String s)
	{
		LOGGER.info(s);
	}
}
