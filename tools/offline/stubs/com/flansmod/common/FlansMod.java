package com.flansmod.common;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offline-only bootstrap shim. Never included in sourceSets.main or the game jar.
 * Definition readers, bytecode extraction and baking remain the production classes.
 */
public final class FlansMod {
    public static final String MODID = "flansmod";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("flansmod-offline");
    public static Path flanDir;
    public static final Map<String,Path> contentPacks = new LinkedHashMap<>();
    public static void log(String message) { LOGGER.info(message); }
}
