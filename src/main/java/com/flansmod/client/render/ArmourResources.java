package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.ContentPackIndex;
import com.flansmod.common.FlansMod;
import com.flansmod.common.ItemAssets;
import com.flansmod.common.types.ArmourType;
import dev.packarms.core.IndexedPack;
import dev.packarms.core.PackEntry;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A skin belongs to its definition/model pack, even when another edition reuses its filename. */
public final class ArmourResources {
    private ArmourResources() { }
    public record Skin(ResourceLocation original, PackEntry entry) { }
    private record State(long generation, Map<ArmourType,Optional<Skin>> skins) { }
    private static volatile State state = new State(-1, new ConcurrentHashMap<>());

    public static Optional<Skin> source(ArmourType type) {
        var index = ContentPackIndex.current();
        var current = state;
        if(current.generation() != index.generation()) {
            synchronized(ArmourResources.class) {
                if(state.generation() != index.generation())
                    state = new State(index.generation(),new ConcurrentHashMap<>());
                current = state;
            }
        }
        return current.skins().computeIfAbsent(type, armour -> {
            var owner = FlansMod.contentPacks.get(armour.contentPack);
            if(owner != null) {
                var own = index.pack(owner).flatMap(pack -> in(pack,armour));
                if(own.isPresent()) return own;
            }
            // Scav definitions borrow the old EFT model and skin; global first-wins would
            // combine that 1024 atlas with the Reforged edition's unrelated 128 atlas.
            var model = ModelCache.sourcePack(armour.modelString).flatMap(pack -> in(pack,armour));
            if(model.isPresent()) return model;
            for(var pack : index.packs()) {
                var found = in(pack,armour);
                if(found.isPresent()) return found;
            }
            return Optional.empty();
        });
    }

    private static Optional<Skin> in(IndexedPack pack,ArmourType type) {
        var id = Textures.forArmour(type, location -> pack.folded(path(location)).isPresent());
        return id == null ? Optional.empty() : pack.folded(path(id)).map(entry -> new Skin(id,entry));
    }
    private static String path(ResourceLocation id) { return "assets/"+id.getNamespace()+"/"+id.getPath(); }

    /** Matches the native ArmorMaterial layer, so baking and rendering bind the same pixels. */
    public static ResourceLocation texture(ArmourType type) {
        return ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,
                "textures/models/armor/legacy/"+ItemAssets.registryPathOf(type)+"_layer_"+(type.type==2?2:1)+".png");
    }
}
