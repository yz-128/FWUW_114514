package dev.packarms.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** An immutable entry. Each open creates an independent stream; callers must close it. */
public final class PackEntry {
    private final Path source;
    private final String path;
    private final String sourcePath;
    private final Opener opener;

    @FunctionalInterface interface Opener { InputStream open() throws IOException; }

    PackEntry(Path source, String path, String sourcePath, Opener opener) {
        this.source = Objects.requireNonNull(source);
        this.path = Objects.requireNonNull(path);
        this.sourcePath = Objects.requireNonNull(sourcePath);
        this.opener = Objects.requireNonNull(opener);
    }

    public Path source() { return source; }
    /** Pack-root-relative path with forward slashes, preserving filename case. */
    public String path() { return path; }
    /** Original archive/directory-relative path, including any detected wrapper. */
    public String sourcePath() { return sourcePath; }
    public InputStream open() throws IOException { return opener.open(); }
    public byte[] readAllBytes() throws IOException {
        try (InputStream input = open()) { return input.readAllBytes(); }
    }
    @Override public String toString() { return source + "!" + sourcePath; }
}
