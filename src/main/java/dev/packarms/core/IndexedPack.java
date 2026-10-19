package dev.packarms.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable lookup tables in scan order. No query enumerates the filesystem/archive again. */
public final class IndexedPack implements Closeable {
    private final Path source;
    private final String rootPrefix;
    private final boolean archive;
    private final List<PackEntry> entries;
    private final Map<String, PackEntry> exact = new LinkedHashMap<>();
    private final Map<String, PackEntry> folded = new LinkedHashMap<>();
    private final Map<String, PackEntry> suffixExact = new LinkedHashMap<>();
    private final Map<String, PackEntry> suffixFolded = new LinkedHashMap<>();
    private final Map<String, List<PackEntry>> underExact = new LinkedHashMap<>();
    private final Map<String, List<PackEntry>> underFolded = new LinkedHashMap<>();
    private final Map<String, Set<String>> namespaces = new LinkedHashMap<>();
    private final Closeable sourceHandle;

    IndexedPack(Path source, String rootPrefix, boolean archive, List<PackEntry> entries, Closeable sourceHandle,
                List<ScanProblem> problems) {
        this.source = source;
        this.rootPrefix = rootPrefix;
        this.archive = archive;
        this.entries = List.copyOf(entries);
        this.sourceHandle = sourceHandle;
        for (PackEntry entry : this.entries) {
            String name = entry.path();
            PackEntry duplicate = exact.putIfAbsent(name, entry);
            if (duplicate != null) problems.add(new ScanProblem(source, entry.sourcePath(),
                    "Duplicate exact path; first entry wins: " + duplicate.sourcePath()));
            String lower = PackPaths.folded(name);
            PackEntry caseConflict = folded.putIfAbsent(lower, entry);
            if (caseConflict != null && !caseConflict.path().equals(name)) {
                problems.add(new ScanProblem(source, entry.sourcePath(),
                        "Case-folded path collision; folded queries keep first entry: " + caseConflict.sourcePath()));
            }
            for (int start = 0; start >= 0; ) {
                String suffix = name.substring(start);
                suffixExact.putIfAbsent(suffix, entry);
                suffixFolded.putIfAbsent(PackPaths.folded(suffix), entry);
                int slash = name.indexOf('/', start);
                start = slash < 0 ? -1 : slash + 1;
            }
            underExact.computeIfAbsent("", ignored -> new ArrayList<>()).add(entry);
            underFolded.computeIfAbsent("", ignored -> new ArrayList<>()).add(entry);
            for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                String prefix = name.substring(0, slash);
                underExact.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(entry);
                underFolded.computeIfAbsent(PackPaths.folded(prefix), ignored -> new ArrayList<>()).add(entry);
            }
            int first = name.indexOf('/');
            int second = first < 0 ? -1 : name.indexOf('/', first + 1);
            if (second > first + 1) {
                String tree = PackPaths.folded(name.substring(0, first));
                if (tree.equals("assets") || tree.equals("data")) {
                    namespaces.computeIfAbsent(tree, ignored -> new LinkedHashSet<>())
                            .add(name.substring(first + 1, second));
                }
            }
        }
        underExact.replaceAll((key, value) -> List.copyOf(value));
        underFolded.replaceAll((key, value) -> List.copyOf(value));
        namespaces.replaceAll((key, value) -> Collections.unmodifiableSet(value));
    }

    public Path source() { return source; }
    public String name() { return source.getFileName() == null ? source.toString() : source.getFileName().toString(); }
    public boolean archive() { return archive; }
    public String rootPrefix() { return rootPrefix; }
    public List<PackEntry> entries() { return entries; }
    public Optional<PackEntry> exact(String path) { return Optional.ofNullable(exact.get(PackPaths.normalize(path))); }
    /** Exact spelling wins within this pack, otherwise first case-folded entry wins. */
    public Optional<PackEntry> folded(String path) {
        String name = PackPaths.normalize(path);
        PackEntry found = exact.get(name);
        return Optional.ofNullable(found != null ? found : folded.get(PackPaths.folded(name)));
    }
    /** Recursive files under a directory, in scan order; exact directory spelling precedes folded fallback. */
    public List<PackEntry> under(String prefix) {
        String name = PackPaths.normalize(prefix);
        List<PackEntry> found = underExact.get(name);
        return found != null ? found : underFolded.getOrDefault(PackPaths.folded(name), List.of());
    }
    /** Historical class archives sometimes nest the class tree under an unrelated directory. */
    public Optional<PackEntry> suffix(String path) {
        String name = PackPaths.normalize(path);
        PackEntry found = exact.get(name);
        if (found == null) found = suffixExact.get(name);
        if (found == null) found = suffixFolded.get(PackPaths.folded(name));
        return Optional.ofNullable(found);
    }
    /** Namespace spelling is preserved; an integration can enforce its own ID grammar. */
    public Set<String> namespaces(String assetsOrData) {
        return namespaces.getOrDefault(PackPaths.folded(PackPaths.normalize(assetsOrData)), Set.of());
    }
    @Override public void close() throws IOException { sourceHandle.close(); }
}
