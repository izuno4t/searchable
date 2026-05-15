package com.searchable.plugin;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Plugin-friendly representation of a document fetched from an external source.
 *
 * <p>This type is intentionally separate from the core {@code Document} class
 * to keep the plugin SPI free of dependencies on the core implementation.
 *
 * @param id            unique identifier within the data source
 * @param title         document title
 * @param content       textual content (will be tokenized by the analyzer)
 * @param sourceType    short name of the source (e.g. {@code file}, {@code url})
 * @param sourceLocation pointer to the original source
 * @param contentHash   hash for change detection (nullable)
 * @param sourceUpdated last modification time at the source (nullable)
 * @param metadata      arbitrary key/value metadata (may be empty)
 */
public record PluginDocument(
    String id,
    String title,
    String content,
    String sourceType,
    String sourceLocation,
    String contentHash,
    Instant sourceUpdated,
    Map<String, Object> metadata
) {

    public PluginDocument {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(sourceLocation, "sourceLocation must not be null");
        if (id.isBlank() || title.isBlank() || sourceType.isBlank() || sourceLocation.isBlank()) {
            throw new IllegalArgumentException("required fields must not be blank");
        }
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
