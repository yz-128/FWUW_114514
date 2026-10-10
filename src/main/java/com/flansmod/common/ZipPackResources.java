package com.flansmod.common;

import java.nio.file.Path;
import net.minecraft.server.packs.PackLocationInfo;

/** Archive streams and UTF-8/GBK names are managed by the same indexed pack core as folders. */
public final class ZipPackResources extends IndexedPackResources {
    private ZipPackResources(PackLocationInfo info, Path source) { super(info, source); }
    public static ZipPackResources open(PackLocationInfo info, Path source) {
        var resources = new ZipPackResources(info, source);
        var pack = resources.indexed();
        return pack != null && !pack.under("assets/").isEmpty() ? resources : null;
    }
}
