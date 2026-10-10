package com.flansmod.common;

import dev.packarms.core.IndexedPack;
import dev.packarms.core.PackEntry;
import dev.packarms.core.PackIndex;
import dev.packarms.core.ScanPolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/** Folder and archive adapters share the exact same indexed resolution rules. */
abstract class IndexedPackResources extends AbstractPackResources implements ContentPackResources.CaseInsensitive {
    private final Path source;
    private PackIndex standalone;
    private volatile Rewrites rewrites = new Rewrites(-1L);
    private record Rewrites(long generation, ConcurrentHashMap<String, Optional<byte[]>> files) {
        Rewrites(long generation) { this(generation, new ConcurrentHashMap<>()); }
    }

    IndexedPackResources(PackLocationInfo info, Path source) {
        super(info);
        this.source = source.toAbsolutePath().normalize();
    }

    protected IndexedPack indexed() {
        var shared = ContentPackIndex.current().pack(source);
        if(shared.isPresent()) return shared.get();
        synchronized(this) {
            try {
                if(standalone == null) standalone = PackIndex.scanOrdered(List.of(source), ScanPolicy.legacy());
                return standalone.pack(source).orElse(null);
            } catch(IOException failure) {
                FlansMod.LOGGER.warn("Could not index {}", source, failure);
                return null;
            }
        }
    }

    @Override public IoSupplier<InputStream> getRootResource(String... path) {
        var pack = indexed();
        if(pack == null || path.length == 0) return null;
        return pack.exact(String.join("/", path)).map(IndexedPackResources::supplier).orElse(null);
    }

    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        var pack = indexed();
        if(pack == null) return null;
        String path = type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath();
        var entry = pack.exact(path).or(() -> pack.folded(path));
        if(entry.isEmpty()) return null;
        if(type != PackType.CLIENT_RESOURCES || !SoundJsonRewriter.needsRewrite(location.getPath()))
            return supplier(entry.get());
        long generation = ContentPackIndex.current().generation();
        var cache = rewrites;
        if(cache.generation() != generation) rewrites = cache = new Rewrites(generation);
        var bytes = cache.files().computeIfAbsent(path, key -> {
            try(var stream = supplier(entry.get()).get()) {
                return Optional.of(SoundJsonRewriter.rewrite(stream.readAllBytes()));
            }
            catch(IOException | RuntimeException failure) {
                FlansMod.LOGGER.warn("Could not rewrite {} in {}", location, pack.source(), failure);
                return Optional.empty();
            }
        });
        return bytes.<IoSupplier<InputStream>>map(data -> () -> new ByteArrayInputStream(data)).orElse(null);
    }

    @Override public IoSupplier<InputStream> getResourceFolded(PackType type, ResourceLocation location) {
        return getResource(type, location);
    }

    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        var pack = indexed();
        if(pack == null) return;
        String prefix = type.getDirectory() + "/" + namespace + "/";
        String under = prefix + (path.isEmpty() ? "" : path + "/");
        for(var entry : pack.under(under)) {
            var id = ResourceLocation.tryBuild(namespace, entry.path().substring(prefix.length()).toLowerCase(Locale.ROOT));
            if(id != null) output.accept(id, supplier(entry));
        }
    }

    @Override public Set<String> getNamespaces(PackType type) {
        var pack = indexed();
        return pack == null ? Set.of() : pack.namespaces(type.getDirectory() + "/");
    }

    private static IoSupplier<InputStream> supplier(PackEntry entry) {
        // A supplier retained by Minecraft across reload must not reopen a retired archive handle.
        return () -> {
            synchronized(ContentPackIndex.class) {
                var pack = ContentPackIndex.current().pack(entry.source());
                return pack.isPresent() ? pack.get().exact(entry.path()).orElseThrow(
                        () -> new IOException("Resource was removed: " + entry)).open() : entry.open();
            }
        };
    }

    // The shared index owns handles; Minecraft may reopen this same resource adapter on reload.
    @Override public void close() { }
}
