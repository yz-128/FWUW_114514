package dev.packarms.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Root detection and archive-name decoding only; this class never parses game definitions. */
public record ScanPolicy(Set<String> rootMarkers, List<Charset> archiveCharsets,
                         int maxWrapperDepth, boolean skipHiddenEntries) {
    public ScanPolicy {
        Objects.requireNonNull(rootMarkers, "rootMarkers");
        Objects.requireNonNull(archiveCharsets, "archiveCharsets");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String marker : rootMarkers) {
            if (marker == null || marker.isBlank() || marker.indexOf('/') >= 0 || marker.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("A root marker must be one path component: " + marker);
            }
            normalized.add(marker.toLowerCase(Locale.ROOT));
        }
        rootMarkers = java.util.Collections.unmodifiableSet(normalized);
        archiveCharsets = List.copyOf(archiveCharsets);
        if (archiveCharsets.isEmpty()) throw new IllegalArgumentException("At least one archive charset is required");
        if (maxWrapperDepth < 0) throw new IllegalArgumentException("maxWrapperDepth must be nonnegative");
    }

    private static List<Charset> compatibleCharsets() {
        return List.of(StandardCharsets.UTF_8, Charset.forName("GBK"), Charset.forName("GB18030"));
    }

    /** One wrapper level matches legacy pack-root discovery. Enumeration is deliberately unsorted. */
    public static ScanPolicy legacy() {
        return new ScanPolicy(Set.of("guns", "vehicles", "planes", "bullets", "attachments", "parts",
                "grenades", "tools", "boxes", "aas", "aaguns", "mechas", "mechaitems", "classes",
                "teams", "armorfiles", "armorboxes", "assets", "com"), compatibleCharsets(), 1, false);
    }

    public static ScanPolicy modern() {
        return new ScanPolicy(Set.of("assets", "data", "pack.mcmeta", "packarms.json", "pack.json"),
                compatibleCharsets(), 32, true);
    }

    public ScanPolicy withRootMarkers(Set<String> markers) {
        return new ScanPolicy(markers, archiveCharsets, maxWrapperDepth, skipHiddenEntries);
    }

    public ScanPolicy withWrapperDepth(int depth) {
        return new ScanPolicy(rootMarkers, archiveCharsets, depth, skipHiddenEntries);
    }

    public ScanPolicy withHiddenEntries(boolean skip) {
        return new ScanPolicy(rootMarkers, archiveCharsets, maxWrapperDepth, skip);
    }
}
