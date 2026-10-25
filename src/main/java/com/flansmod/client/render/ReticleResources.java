package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.ContentPackIndex;
import com.flansmod.common.FlansMod;
import com.flansmod.common.ItemAssets;
import com.flansmod.common.types.AttachmentType;
import dev.packarms.core.IndexedPack;
import dev.packarms.core.PackEntry;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Shared names such as DefaultDot belong to their definition's pack, as do armour skins. */
public final class ReticleResources {
    private ReticleResources() { }
    public record Skin(ResourceLocation original,PackEntry entry) { }
    private record Key(AttachmentType type,int frame) { }
    private record State(long generation,Map<Key,Optional<Skin>> skins) { }
    private static volatile State state=new State(-1,new ConcurrentHashMap<>());
    public static ResourceLocation texture(AttachmentType type,int frame) {
        return ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"textures/reticles/legacy/"
                +ItemAssets.registryPathOf(type)+"/"+Math.clamp(frame,0,Math.max(0,type.lightTextureCount-1))+".png");
    }
    public static Optional<Skin> source(AttachmentType type,int frame) {
        var index=ContentPackIndex.current();var current=state;
        if(current.generation()!=index.generation())synchronized(ReticleResources.class){
            if(state.generation()!=index.generation())state=new State(index.generation(),new ConcurrentHashMap<>());
            current=state;
        }
        return current.skins().computeIfAbsent(new Key(type,Math.clamp(frame,0,Math.max(0,type.lightTextureCount-1))),key->{
            var owner=FlansMod.contentPacks.get(type.contentPack);
            if(owner!=null){var own=index.pack(owner).flatMap(p->in(p,type,key.frame()));if(own.isPresent())return own;}
            var model=ModelCache.sourcePack(type.modelString).flatMap(p->in(p,type,key.frame()));
            if(model.isPresent())return model;
            for(var pack:index.packs()){var skin=in(pack,type,key.frame());if(skin.isPresent())return skin;}
            return Optional.empty();
        });
    }
    private static Optional<Skin> in(IndexedPack pack,AttachmentType type,int frame) {
        var id=Textures.forReticle(type,frame,key->pack.folded("assets/"+key.getNamespace()+"/"+key.getPath()).isPresent());
        return id==null?Optional.empty():pack.folded("assets/"+id.getNamespace()+"/"+id.getPath()).map(entry->new Skin(id,entry));
    }
}
