package dev.packarms.core;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Ordered, immutable pack snapshot shared by registration, resource lookup and lazy asset readers.
 * Input pack order and the filesystem/archive's encountered entry order are never sorted.
 * Invalid packs are recorded in problems and skipped; one broken pack does not hide later packs.
 */
public final class PackIndex implements Closeable {
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private final long generation = NEXT_GENERATION.incrementAndGet();
    private final List<IndexedPack> packs;
    private final List<ScanProblem> problems;
    private final Map<Path, IndexedPack> bySource;
    private final AtomicBoolean closed = new AtomicBoolean();

    private PackIndex(List<IndexedPack> packs, List<ScanProblem> problems) {
        this.packs = List.copyOf(packs);
        this.problems = List.copyOf(problems);
        LinkedHashMap<Path, IndexedPack> bySource = new LinkedHashMap<>();
        for (IndexedPack pack : packs) bySource.putIfAbsent(pack.source(), pack);
        this.bySource = java.util.Collections.unmodifiableMap(bySource);
    }

    public static PackIndex scanOrdered(List<Path> sources, ScanPolicy policy) throws IOException {
        return scanOrdered(sources, policy, false);
    }

    /** Archive bytes belong to this generation, including entries not yet read by a lazy worker. */
    public static PackIndex scanSnapshotOrdered(List<Path> sources, ScanPolicy policy) throws IOException {
        return scanOrdered(sources, policy, true);
    }

    private static PackIndex scanOrdered(List<Path> sources, ScanPolicy policy, boolean snapshotArchives) throws IOException {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(policy, "policy");
        List<IndexedPack> packs = new ArrayList<>();
        List<ScanProblem> problems = new ArrayList<>();
        for (Path provided : sources) {
            Path source = Objects.requireNonNull(provided, "pack source").toAbsolutePath().normalize();
            try {
                IndexedPack pack;
                if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                    pack = scanDirectory(source, policy, problems);
                } else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                        && isArchive(source)) {
                    pack = scanArchive(source, policy, problems, snapshotArchives);
                } else {
                    problems.add(new ScanProblem(source, "", "Not a directory, zip or jar pack"));
                    continue;
                }
                packs.add(pack);
            } catch (IOException | IllegalArgumentException error) {
                problems.add(new ScanProblem(source, "", "Cannot index pack: " + error.getMessage()));
            }
        }
        return new PackIndex(packs, problems);
    }

    public List<IndexedPack> packs() { return packs; }
    public long generation() { return generation; }
    public List<ScanProblem> problems() { return problems; }
    public Optional<IndexedPack> pack(Path source) {
        return Optional.ofNullable(bySource.get(source.toAbsolutePath().normalize()));
    }
    /** First pack wins: exact THEN folded in each individual pack, not two passes across all packs. */
    public Optional<PackEntry> find(String path, boolean caseInsensitive) {
        for (IndexedPack pack : packs) {
            Optional<PackEntry> entry = caseInsensitive ? pack.folded(path) : pack.exact(path);
            if (entry.isPresent()) return entry;
        }
        return Optional.empty();
    }

    private static boolean isArchive(Path path) {
        String name = PackPaths.folded(path.getFileName().toString());
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private record RawEntry(String name, String originalName, boolean directory) { }

    private static IndexedPack scanDirectory(Path source, ScanPolicy policy,
                                             List<ScanProblem> problems) throws IOException {
        List<RawEntry> raw = new ArrayList<>();
        walk(source, source, policy, raw, problems);
        String prefix = findRoot(raw, policy);
        DirectoryHandle handle = new DirectoryHandle(source);
        List<PackEntry> entries = new ArrayList<>();
        for (RawEntry entry : raw) {
            if (entry.directory || !entry.name.startsWith(prefix)) continue;
            String relative = entry.name.substring(prefix.length());
            if (relative.isEmpty()) continue;
            entries.add(new PackEntry(source, relative, entry.originalName,
                    () -> handle.open(entry.originalName)));
        }
        return new IndexedPack(source, prefix, false, entries, handle, problems);
    }

    private static void walk(Path source, Path directory, ScanPolicy policy,
                             List<RawEntry> entries, List<ScanProblem> problems) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                String name = source.relativize(child).toString().replace('\\', '/');
                if (policy.skipHiddenEntries() && PackPaths.hidden(name)) continue;
                if (Files.isSymbolicLink(child)) {
                    problems.add(new ScanProblem(source, name, "Symbolic link omitted from pack index"));
                } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    entries.add(new RawEntry(name, name, true));
                    try { walk(source, child, policy, entries, problems); }
                    catch (IOException error) {
                        problems.add(new ScanProblem(source, name, "Cannot index directory: " + error.getMessage()));
                    }
                } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    entries.add(new RawEntry(name, name, false));
                }
            }
        }
    }

    private static IndexedPack scanArchive(Path source, ScanPolicy policy,
                                           List<ScanProblem> problems, boolean snapshotArchive) throws IOException {
        Path backing = snapshotArchive ? copyArchive(source) : source;
        boolean retained = false;
        try {
            IndexedPack pack = scanArchiveBytes(source, backing, policy, problems, snapshotArchive);
            retained = true;
            return pack;
        } finally {
            if (snapshotArchive && !retained) Files.deleteIfExists(backing);
        }
    }

    private static IndexedPack scanArchiveBytes(Path source, Path backing, ScanPolicy policy,
                                                List<ScanProblem> problems, boolean snapshotArchive) throws IOException {
        List<RawEntry> raw = null;
        Charset selected = null;
        IOException failure = null;
        for (Charset charset : policy.archiveCharsets()) {
            List<RawEntry> candidate = new ArrayList<>();
            List<ScanProblem> candidateProblems = new ArrayList<>();
            try (ZipFile zip = new ZipFile(backing.toFile(), charset)) {
                Enumeration<? extends ZipEntry> archiveEntries = zip.entries();
                while (archiveEntries.hasMoreElements()) {
                    ZipEntry entry = archiveEntries.nextElement();
                    String originalName = entry.getName();
                    String name;
                    try {
                        if (originalName.startsWith("/") || originalName.startsWith("\\")) {
                            throw new IllegalArgumentException("Absolute entry path");
                        }
                        name = PackPaths.normalize(originalName);
                    } catch (IllegalArgumentException error) {
                        candidateProblems.add(new ScanProblem(source, originalName, "Unsafe archive entry omitted"));
                        continue;
                    }
                    if (name.isEmpty() || (policy.skipHiddenEntries() && PackPaths.hidden(name))) continue;
                    candidate.add(new RawEntry(name, originalName, entry.isDirectory()));
                }
                raw = candidate;
                selected = charset;
                problems.addAll(candidateProblems);
                break;
            } catch (IOException | IllegalArgumentException error) {
                IOException attempt = new IOException("Archive filename decoding with " + charset.name()
                        + " failed: " + error.getMessage(), error);
                if (failure == null) failure = attempt;
                else failure.addSuppressed(attempt);
            }
        }
        if (raw == null) throw Objects.requireNonNull(failure);
        String prefix = findRoot(raw, policy);
        ArchiveHandle handle = new ArchiveHandle(source, backing, selected, snapshotArchive);
        List<PackEntry> entries = new ArrayList<>();
        for (RawEntry entry : raw) {
            if (entry.directory || !entry.name.startsWith(prefix)) continue;
            String relative = entry.name.substring(prefix.length());
            if (relative.isEmpty()) continue;
            entries.add(new PackEntry(source, relative, entry.originalName,
                    () -> handle.open(entry.originalName)));
        }
        return new IndexedPack(source, prefix, true, entries, handle, problems);
    }

    private static Path copyArchive(Path source) throws IOException {
        Path copy = Files.createTempFile("packarms-archive-", ".zip");
        boolean retained = false;
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                BasicFileAttributes before = Files.readAttributes(source, BasicFileAttributes.class);
                Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
                BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class);
                if (before.size() == after.size() && Files.size(copy) == after.size()
                        && before.lastModifiedTime().equals(after.lastModifiedTime())
                        && Objects.equals(before.fileKey(), after.fileKey())) {
                    copy.toFile().deleteOnExit();
                    retained = true;
                    return copy;
                }
            }
            throw new IOException("Archive changed while making a content snapshot: " + source);
        } finally {
            if (!retained) Files.deleteIfExists(copy);
        }
    }

    private static String findRoot(List<RawEntry> entries, ScanPolicy policy) {
        if (hasMarker(entries, "", policy.rootMarkers())) return "";
        String prefix = "";
        for (int depth = 0; depth < policy.maxWrapperDepth(); depth++) {
            Set<String> directories = new LinkedHashSet<>();
            for (RawEntry entry : entries) {
                if (!entry.name.startsWith(prefix)) continue;
                String rest = entry.name.substring(prefix.length());
                int slash = rest.indexOf('/');
                if (slash >= 0) directories.add(rest.substring(0, slash));
                else if (entry.directory && !rest.isEmpty()) directories.add(rest);
            }
            if (directories.size() != 1) return "";
            prefix += directories.iterator().next() + "/";
            if (hasMarker(entries, prefix, policy.rootMarkers())) return prefix;
        }
        return "";
    }

    private static boolean hasMarker(List<RawEntry> entries, String prefix, Set<String> markers) {
        for (RawEntry entry : entries) {
            if (!entry.name.startsWith(prefix)) continue;
            String rest = entry.name.substring(prefix.length());
            int slash = rest.indexOf('/');
            String component = slash < 0 ? rest : rest.substring(0, slash);
            if (markers.contains(PackPaths.folded(component))) return true;
        }
        return false;
    }

    private static final class DirectoryHandle implements Closeable {
        private final Path source;
        private boolean closed;
        private DirectoryHandle(Path source) { this.source = source; }
        synchronized InputStream open(String name) throws IOException {
            if (closed) throw new IOException("Pack index is closed: " + source);
            Path path = source.resolve(name).normalize();
            if (!path.startsWith(source)) throw new IOException("Entry escaped pack source: " + name);
            return Files.newInputStream(path);
        }
        @Override public synchronized void close() { closed = true; }
    }

    /** Lazily reopened once; active entry streams hold a lease until their own close. */
    private static final class ArchiveHandle implements Closeable {
        private final Path source;
        private final Path backing;
        private final boolean privateCopy;
        private final Charset charset;
        private ZipFile zip;
        private int leases;
        private boolean closed;
        private ArchiveHandle(Path source, Path backing, Charset charset, boolean privateCopy) {
            this.source = source; this.backing = backing; this.charset = charset; this.privateCopy = privateCopy;
        }

        synchronized InputStream open(String name) throws IOException {
            if (closed) throw new IOException("Pack index is closed: " + source);
            if (zip == null) zip = new ZipFile(backing.toFile(), charset);
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) throw new IOException("Indexed entry no longer exists: " + source + "!" + name);
            InputStream stream = zip.getInputStream(entry);
            leases++;
            return new FilterInputStream(stream) {
                private final AtomicBoolean released = new AtomicBoolean();
                @Override public void close() throws IOException {
                    if (!released.compareAndSet(false, true)) return;
                    IOException failure = null;
                    try { super.close(); } catch (IOException error) { failure = error; }
                    try { release(); } catch (IOException error) {
                        if (failure == null) failure = error;
                        else failure.addSuppressed(error);
                    }
                    if (failure != null) throw failure;
                }
            };
        }

        private synchronized void release() throws IOException {
            leases--;
            if (closed && leases == 0) closeZip();
        }
        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            if (leases == 0) closeZip();
        }
        private void closeZip() throws IOException {
            IOException failure = null;
            if (zip != null) {
                ZipFile toClose = zip;
                zip = null;
                try { toClose.close(); } catch (IOException error) { failure = error; }
            }
            if (privateCopy) try { Files.deleteIfExists(backing); }
            catch (IOException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
            if (failure != null) throw failure;
        }
    }

    @Override public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        IOException failure = null;
        for (IndexedPack pack : packs) {
            try { pack.close(); }
            catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
