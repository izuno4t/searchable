package io.searchable.core.domain.document;

import java.time.Instant;
import java.util.Objects;

/**
 * Origin information of an indexed document.
 *
 * @param type           source type identifier (e.g. {@code file}, {@code url}, {@code plugin:my-plugin})
 * @param location       source location (file path, URL, etc.)
 * @param contentHash    hash of the source content for change detection (nullable)
 * @param sourceUpdated  timestamp of the latest source modification (nullable)
 */
public record DocumentSource(String type,
                             String location,
                             String contentHash,
                             Instant sourceUpdated) {

    public DocumentSource {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(location, "location must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
    }

    public static DocumentSource of(final String type, final String location) {
        return new DocumentSource(type, location, null, null);
    }
}
