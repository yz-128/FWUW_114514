package com.flansmod.client.render;

import com.flansmod.common.FlansMod;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ShootableType;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * One place that decides which file a definition's {@code Texture} field refers to.
 *
 * <p>Three callers need the same answer — the item renderer, the bullet renderer and the model
 * baker's texture-size check — and they must agree: if the baker corrects UVs against one file
 * while the renderer binds another, the correction is applied to the wrong art.
 *
 * <p>Two layouts are in the wild and both are tried, in this order:
 * <ul>
 *   <li>{@code assets/flansmod/skins/<name>.png} — where 1.7.10 looked and where every pack that
 *       has not been reprocessed keeps its skins;</li>
 *   <li>{@code assets/flansmod/textures/skins/<name>.png} — where the 1.20.1 lineage moves them
 *       when it preprocesses a pack, with the file names lower-cased as it copies.</li>
 * </ul>
 *
 * <p>Names are lower-cased because a resource location cannot carry capitals, and the packs are
 * matched case-insensitively by the resource packs themselves. A name with a namespace is used as
 * written.
 */
public final class Textures
{
	private Textures() { }
	private record Key(String name, boolean fallback) { }
	private static volatile java.util.Map<Key, java.util.Optional<ResourceLocation>> resolved =
			new java.util.concurrent.ConcurrentHashMap<>();
	/** A fresh map prevents in-flight lookups from publishing into the new resource generation. */
	public static void clear() { resolved = new java.util.concurrent.ConcurrentHashMap<>(); }

	/** The stock round's skin, used when a bullet definition names none. */
	public static final String DEFAULT_BULLET = "defaultBullet";

	/** Where this definition's skin lives, or null when it names none. */
	@Nullable
	public static ResourceLocation forType(InfoType type)
	{
		return of(type == null ? null : type.texture, false);
	}

	/** Same lookup for offline pack inspection, with a filesystem-backed resource probe. */
	@Nullable
	public static ResourceLocation forType(InfoType type, java.util.function.Predicate<ResourceLocation> resourceExists)
	{
		return of(type == null ? null : type.texture, false, resourceExists);
	}

	/** Where a bullet's skin lives, falling back to the stock round. */
	@Nullable
	public static ResourceLocation forBullet(ShootableType bullet)
	{
		return of(bullet == null ? null : bullet.texture, true);
	}

	/**
	 * Resolves a bare texture name.
	 *
	 * @param fallbackToDefault whether an absent name means the stock round's skin
	 */
	@Nullable
	public static ResourceLocation of(String texture, boolean fallbackToDefault)
	{
		return resolved.computeIfAbsent(new Key(texture, fallbackToDefault),
				key -> java.util.Optional.ofNullable(of(key.name(), key.fallback(), Textures::has))).orElse(null);
	}

	@Nullable
	public static ResourceLocation of(String texture, boolean fallbackToDefault,
			java.util.function.Predicate<ResourceLocation> resourceExists)
	{
		if(texture == null || texture.isBlank())
		{
			if(!fallbackToDefault)
				return null;
			texture = DEFAULT_BULLET;
		}

		if(texture.contains(":"))
			return ResourceLocation.tryParse(texture.toLowerCase(java.util.Locale.ROOT));

		String name = texture.toLowerCase(java.util.Locale.ROOT);
		ResourceLocation legacy = ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,
				"skins/" + name + ".png");
		if(resourceExists.test(legacy))
			return legacy;
		return ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "textures/skins/" + name + ".png");
	}

	/** True when the resource manager can actually find this texture in some loaded pack. */
	public static boolean has(ResourceLocation location)
	{
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.getResourceManager() != null
				&& mc.getResourceManager().getResource(location).isPresent();
	}
}
