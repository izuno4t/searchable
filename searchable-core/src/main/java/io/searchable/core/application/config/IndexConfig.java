package io.searchable.core.application.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration of Lucene index storage.
 *
 * @param directory root directory under which per-namespace indexes are stored
 */
public record IndexConfig(Path directory) {

    public IndexConfig {
        Objects.requireNonNull(directory, "directory must not be null");
    }

    public static IndexConfig defaults() {
        return new IndexConfig(Path.of("./data/indexes"));
    }
}
