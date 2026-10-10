package com.flansmod.common;

import java.nio.file.Path;
import net.minecraft.server.packs.PackLocationInfo;

/** Loose folders use the shared index, including the historical case-insensitive fallback. */
public final class FolderPackResources extends IndexedPackResources {
    public FolderPackResources(PackLocationInfo info, Path source) { super(info, source); }
}
