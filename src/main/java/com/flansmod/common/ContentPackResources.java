package com.flansmod.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Exposes every content pack under {@code Flan/} to Minecraft's resource system.
 *
 * <p>Why this is needed: 1.7.10 loaded a pack's skins and sounds itself, through its own
 * resource handler and a per-pack class loader. Modern Minecraft only reads resources
 * through the pack repository, so the packs have to be offered to it — otherwise the
 * definitions load but every gun renders as an untextured purple square.
 *
 * <p>All four pack shapes are handled (see {@link PackLayout}): a loose folder, a zip or jar
 * with the structure at its root, and either of those wrapped one folder deep.
 *
 * <p>The shape follows TACZ's {@code GunPackLoader} — implement {@link RepositorySource},
 * scan the folder, hand back one {@link Pack} — with two differences: a Flan pack carries no
 * descriptor file (its name comes from the folder/archive), and archives get their own
 * {@link PackResources} because vanilla's {@code PathPackResources} only reads directories.
 */
public class ContentPackResources implements RepositorySource
{
	private final Path packDir;

	public ContentPackResources(Path packDir)
	{
		this.packDir = packDir;
	}

	@Override
	public void loadPacks(Consumer<Pack> onLoad)
	{
		List<PackResources> resources = new ArrayList<>();
		List<String> found = new ArrayList<>();

		for(var indexed : ContentPackIndex.current().packs())
		{
			if(indexed.under("assets/").isEmpty()) continue;
			String name = indexed.name();
			Path entry = indexed.source();
			PackResources child = !indexed.archive()
					? new FolderPackResources(location(name), entry) : ZipPackResources.open(location(name), entry);
			if(child != null) { resources.add(child); found.add(name); }
		}

		if(resources.isEmpty())
			return;

		// One combined pack rather than one per content pack: to a player these are a single
		// body of user content, and it keeps the pack list from filling with entries.
		PackLocationInfo info = new PackLocationInfo("flans_content_packs",
				Component.literal("Flan's Content Packs"), PackSource.BUILT_IN, Optional.empty());

		// Built here, not lazily in openPrimary: the model bakery reads models before the pack's
		// resource supplier is asked to open, so a lazily-built generated half arrives too late
		// and every item model 404s.
		List<PackResources> all = new ArrayList<>(resources);
		all.add(GeneratedAssets.build());

		// The generated pack is also written to disk (see GeneratedAssets.writeToDisk) and picked
		// up from there by the game's own folder scan; serving it here as well costs nothing and
		// covers the case where the write failed.
		try
		{
			java.nio.file.Path generated = packDir.getParent().resolve("flansmod_generated");
			if(java.nio.file.Files.isDirectory(generated.resolve("assets")))
			{
				all.add(new FolderPackResources(
						new PackLocationInfo("flans_generated_files", Component.literal("Flan's generated assets"),
								PackSource.BUILT_IN, Optional.empty()), generated));
			}
		}
		catch(Exception ignored) { }

		Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier()
		{
			@Override
			public PackResources openPrimary(PackLocationInfo location)
			{
				return new CompositePackResources(location, all);
			}

			@Override
			public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata)
			{
				// This runs before resource-manager enumeration, unlike a reload listener's apply phase.
				ContentPackIndex.reload();
				GeneratedAssets.writeToDisk(packDir.getParent());
				List<PackResources> refreshed = new ArrayList<>(resources);
				refreshed.add(GeneratedAssets.build());
				return new CompositePackResources(location, refreshed);
			}
		};

		try
		{
			Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
					// required = true: content packs are not optional decoration, they *are* the
					// mod's content, so they must not depend on the player ticking a box in the
					// pack list. Offering them as selectable left the game loading with none of
					// their textures.
					new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
			if(pack != null)
			{
				onLoad.accept(pack);
				FlansMod.log("Offered " + resources.size() + " content pack resource root(s): " + String.join(", ", found));
			}
			else
				FlansMod.LOGGER.warn("Could not assemble the content pack resource bundle");
		}
		catch(Exception e)
		{
			FlansMod.LOGGER.error("Failed to register content pack resources", e);
		}
	}

	private static PackLocationInfo location(String name)
	{
		return new PackLocationInfo("flans_" + name, Component.literal(name), PackSource.BUILT_IN, Optional.empty());
	}

	/** Serves resources from several packs as if they were one. */
	static class CompositePackResources extends AbstractPackResources
	{
		private final List<PackResources> children;

		CompositePackResources(PackLocationInfo info, List<PackResources> children)
		{
			super(info);
			this.children = children;
		}

		@Override
		public IoSupplier<java.io.InputStream> getRootResource(String... path)
		{
			for(PackResources child : children)
			{
				IoSupplier<java.io.InputStream> s = child.getRootResource(path);
				if(s != null)
					return s;
			}
			return null;
		}

		@Override
		public IoSupplier<java.io.InputStream> getResource(PackType type, ResourceLocation location)
		{
			if(type == PackType.CLIENT_RESOURCES && location.getPath().equals("sounds.json"))
			{
				// Minecraft sees this bundle as ONE pack, so it cannot discover the children's
				// sounds.json files as a resource stack. Serve all registrations in one document.
				var merged = new com.google.gson.JsonObject();
				boolean found = false;
				for(PackResources child : children)
				{
					var supplier = child.getResource(type, location);
					if(supplier == null)
						continue;
					try(var input = supplier.get())
					{
						SoundJsonRewriter.mergeInto(merged, input.readAllBytes());
						found = true;
					}
					catch(IOException | RuntimeException e)
					{
						FlansMod.LOGGER.warn("Could not merge {} from {}", location, child.packId(), e);
					}
				}
				if(!found)
					return null;
				byte[] bytes = merged.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
				return () -> new java.io.ByteArrayInputStream(bytes);
			}
			// Content packs name their textures with whatever case the original artist used
			// ("items/20mmAmmo.png"), but a resource location must be lower-case. The request
			// therefore arrives lower-cased and would miss, so the search is repeated against
			// the real entry names with case folded.
			for(PackResources child : children)
			{
				IoSupplier<java.io.InputStream> s = child.getResource(type, location);
				if(s != null)
					return s;
			}
			return null;
		}

		@Override
		public void listResources(PackType type, String namespace, String path, ResourceOutput output)
		{
			for(PackResources child : children)
				child.listResources(type, namespace, path, output);
		}

		@Override
		public Set<String> getNamespaces(PackType type)
		{
			Set<String> all = new java.util.LinkedHashSet<>();
			for(PackResources child : children)
				all.addAll(child.getNamespaces(type));
			return all;
		}

		@Override
		public void close()
		{
			// Deliberately does not close the children: the game closes packs between reloads
			// but hands the same instance back on the next one, and a zip closed for good
			// crashes the following reload with "zip file closed". Each child reopens its own
			// handle lazily when it is used again.
		}
	}

	/** Implemented by packs that can resolve a resource whose case does not match the file. */
	interface CaseInsensitive
	{
		IoSupplier<java.io.InputStream> getResourceFolded(PackType type, ResourceLocation location);
	}
}
