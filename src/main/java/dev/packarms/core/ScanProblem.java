package dev.packarms.core;

import java.nio.file.Path;

/** Nonfatal diagnostics; paths identify the original directory/archive, not a temporary extraction. */
public record ScanProblem(Path source, String entry, String message) {
    @Override public String toString() {
        return source + (entry == null || entry.isEmpty() ? "" : "!" + entry) + ": " + message;
    }
}
