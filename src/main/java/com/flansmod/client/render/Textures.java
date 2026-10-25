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
	private record ReticleKey(com.flansmod.common.types.AttachmentType type,String name,int frame) { }
	private static volatile java.util.Map<ReticleKey, java.util.Optional<ResourceLocation>> reticleResolved =
			new java.util.concurrent.ConcurrentHashMap<>();
	/** A fresh map prevents in-flight lookups from publishing into the new resource generation. */
	public static void clear() {
		resolved = new java.util.concurrent.ConcurrentHashMap<>();
		reticleResolved = new java.util.concurrent.ConcurrentHashMap<>();
	}

	/** The stock round's skin, used when a bullet definition names none. */
	public static final String DEFAULT_BULLET = "defaultBullet";

	/** Where this definition's skin lives, or null when it names none. */
	@Nullable
	public static ResourceLocation forType(InfoType type)
	{
		return of(type == null ? null : type.texture, false);
	}

    /** Native armour rendering and UV checks share a per-item, source-isolated material. */
    public static ResourceLocation forArmour(com.flansmod.common.types.ArmourType type) {
        return ArmourResources.texture(type);
    }

    public static ResourceLocation forArmour(com.flansmod.common.types.ArmourType type,
            java.util.function.Predicate<ResourceLocation> exists) {
        String texture = type.armourTextureName;
        if(texture == null || texture.isBlank()) texture = type.texture;
        if(texture == null || texture.isBlank()) return null;
        texture = texture.toLowerCase(java.util.Locale.ROOT);
        if(texture.contains(":")) return ResourceLocation.tryParse(texture);
        String stem = texture.replaceFirst("\\.png$", "");
        int layer = type.type == 2 ? 2 : 1;
        for(String folder : new String[] {"armor/", "textures/armor/", "textures/models/armor/", "skins/armours/", "textures/skins/armours/"}) {
            var location = ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, folder + stem + "_" + layer + ".png");
            if(exists.test(location)) return location;
        }
        // Both EFT armour editions ship this skin with an accidental duplicated name prefix.
        // Prefer a correctly named replacement/resource pack above, then recognize that exact typo.
        if(stem.equals("maska1sch_killa")) {
            var typo = ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,
                    "armor/maska1schamaska1sch_killa_" + layer + ".png");
            if(exists.test(typo)) return typo;
        }
        return of(texture, false, exists);
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

	private static String followerSkin(com.flansmod.common.types.GunType magazine) {
		String texture=magazine.magFollowerTexture;
		return texture==null || texture.isBlank() || texture.equalsIgnoreCase("None") ? magazine.texture : texture;
	}
	/** None inherits the magazine skin, as the original follower renderer did. */
	public static ResourceLocation forMagazineFollower(com.flansmod.common.types.GunType magazine) {
		return of(followerSkin(magazine),false);
	}
	public static ResourceLocation forMagazineFollower(com.flansmod.common.types.GunType magazine,
			java.util.function.Predicate<ResourceLocation> exists) {
		return of(followerSkin(magazine),false,exists);
	}

	/** A numbered dot frame is preferred, but older packs also ship a single unsuffixed skin. */
	public static ResourceLocation forReticle(com.flansmod.common.types.AttachmentType type,int frame) {
		String name=type.lightStuffTexture;
		if(name==null || name.isBlank()) return forType(type);
		int selected=Math.clamp(frame,0,Math.max(0,type.lightTextureCount-1));
		return reticleResolved.computeIfAbsent(new ReticleKey(type,name,selected),
			key->java.util.Optional.ofNullable(ReticleResources.source(type,selected).isPresent()
					?ReticleResources.texture(type,selected):forReticle(type,selected,Textures::has))).orElse(null);
	}

	public static ResourceLocation forReticle(com.flansmod.common.types.AttachmentType type,int frame,
			java.util.function.Predicate<ResourceLocation> exists) {
		String name=type.lightStuffTexture;
		if(name==null || name.isBlank()) return forType(type,exists);
		int selected=Math.clamp(frame,0,Math.max(0,type.lightTextureCount-1));
		String stem=name.replaceFirst("(?i)\\.png$", "");
		String suffix=stem.contains(":")?".png":"";
		for(String candidate:new String[]{stem+selected,stem+"_"+selected,stem+"0",stem+"_0",stem}) {
			ResourceLocation id=of(candidate+suffix,false,exists);
			if(id!=null && exists.test(id)) return id;
		}
		return of(stem+selected+suffix,false,exists);
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
