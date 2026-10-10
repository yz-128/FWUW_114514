package com.flansmod.client.model.loader;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * The real pixel size of a texture, read from the image header.
 *
 * <p>Needed because a model class bakes its texture coordinates against the size it was exported
 * at, and packs routinely ship the same art at a different resolution — so the model has to be
 * told what the picture actually is before its UVs can be corrected. The size is cached per
 * texture, and only the header is decoded: the pixels are never read, which matters because these
 * are 1024² images and this runs while models are being baked.
 *
 * <p>Client-only: it goes through the resource manager, and returns null off-thread or in a
 * context with no client (the offline bake test), which the callers treat as "no correction".
 */
public final class TextureSizes
{
	private TextureSizes() { }

	/** Width and height, or null when the texture could not be read. */
	private static volatile Map<ResourceLocation, Optional<int[]>> CACHE = new ConcurrentHashMap<>();

	/** The size as {@code {width, height}}, or null when it is unknown. */
	public static int[] of(ResourceLocation texture)
	{
		if(texture == null)
			return null;
		return CACHE.computeIfAbsent(texture, TextureSizes::read).orElse(null);
	}

	private static Optional<int[]> read(ResourceLocation texture)
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc == null || mc.getResourceManager() == null)
			return Optional.empty();

		Optional<Resource> resource = mc.getResourceManager().getResource(texture);
		if(resource.isEmpty())
			return Optional.empty();

		try(InputStream stream = resource.get().open();
			ImageInputStream imageInput = ImageIO.createImageInputStream(stream))
		{
			if(imageInput == null)
				return Optional.empty();

			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
			if(!readers.hasNext())
				return Optional.empty();

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(imageInput, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				return width > 0 && height > 0
						? Optional.of(new int[] { width, height })
						: Optional.empty();
			}
			finally
			{
				reader.dispose();
			}
		}
		catch(Exception e)
		{
			return Optional.empty();
		}
	}

	/** Forget the cached sizes, for a resource reload. */
	public static void clear()
	{
		CACHE = new ConcurrentHashMap<>();
	}
}
