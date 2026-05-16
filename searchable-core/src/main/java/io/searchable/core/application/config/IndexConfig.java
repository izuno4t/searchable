package io.searchable.core.application.config;

import java.nio.file.Path;
import java.util.Objects;

import io.searchable.core.infrastructure.lucene.StorageBackend;

/**
 * Configuration of Lucene index storage.
 *
 * @param directory root directory under which per-namespace indexes are stored
 *                  (used only when {@code backend == FILESYSTEM}; required even
 *                  for {@code MEMORY} for layout-related callers, but its
 *                  content is ignored at runtime)
 * @param backend   storage backend selector (default: {@link StorageBackend#FILESYSTEM})
 */
public record IndexConfig(Path directory, StorageBackend backend) {

    public IndexConfig {
        Objects.requireNonNull(directory, "directory must not be null");
        backend = backend == null ? StorageBackend.FILESYSTEM : backend;
    }

    /** Convenience constructor that defaults to {@link StorageBackend#FILESYSTEM}. */
    public IndexConfig(final Path directory) {
        this(directory, StorageBackend.FILESYSTEM);
    }

    public static IndexConfig defaults() {
        return new IndexConfig(Path.of("./data/indexes"), StorageBackend.FILESYSTEM);
    }
}
