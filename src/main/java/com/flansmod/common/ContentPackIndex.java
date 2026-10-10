package com.flansmod.common;

import dev.packarms.core.PackIndex;
import dev.packarms.core.ScanPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** One ordered file index shared by definitions, geometry and Minecraft resources. */
public final class ContentPackIndex {
    private ContentPackIndex() { }
    private static volatile PackIndex current;
    private static volatile List<Path> sources = List.of();

    public static PackIndex current() {
        var value = current;
        if(value != null && sameSources()) return value;
        synchronized(ContentPackIndex.class) {
            var wanted = List.copyOf(FlansMod.contentPacks.values());
            if(current == null || !sources.equals(wanted)) replace(wanted);
            return current;
        }
    }

    private static boolean sameSources() {
        var expected = sources;
        if(expected.size() != FlansMod.contentPacks.size()) return false;
        int position = 0;
        for(var source : FlansMod.contentPacks.values())
            if(!expected.get(position++).equals(source)) return false;
        return true;
    }

    /** Resources may change on F3+T; registered definitions remain fixed until restart. */
    public static synchronized PackIndex reload() {
        replace(List.copyOf(FlansMod.contentPacks.values()));
        return current;
    }

    private static void replace(List<Path> wanted) {
        try {
            var next = PackIndex.scanOrdered(wanted, ScanPolicy.legacy());
            var previous = current;
            sources = wanted;
            current = next;
            for(var problem : next.problems()) FlansMod.LOGGER.warn("Content pack index: {}", problem);
            if(previous != null) try { previous.close(); }
            catch(IOException closeFailure) { FlansMod.LOGGER.warn("Could not retire old content pack index",closeFailure); }
        } catch(IOException failure) {
            throw new java.io.UncheckedIOException("Could not index content packs", failure);
        }
    }
}
