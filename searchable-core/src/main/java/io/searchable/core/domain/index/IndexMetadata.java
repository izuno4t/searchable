package io.searchable.core.domain.index;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Statistics and lifecycle state of a namespace index.
 */
public record IndexMetadata(
    String namespaceId,
    long documentCount,
    long indexSizeBytes,
    Instant lastUpdated,
    IndexStatus status,
    Map<String, Object> statistics
) {

    public IndexMetadata {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (documentCount < 0) {
            throw new IllegalArgumentException("documentCount must not be negative");
        }
        if (indexSizeBytes < 0) {
            throw new IllegalArgumentException("indexSizeBytes must not be negative");
        }
        statistics = statistics == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(statistics));
    }

    public static IndexMetadata empty(final String namespaceId, final Instant now) {
        return new IndexMetadata(namespaceId, 0L, 0L, now, IndexStatus.EMPTY, Map.of());
    }
}
