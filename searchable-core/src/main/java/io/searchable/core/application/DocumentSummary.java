package io.searchable.core.application;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight document representation used by listing screens.
 *
 * @param id              document identifier
 * @param namespaceId     namespace the document belongs to
 * @param title           document title (stored field)
 * @param snippet         beginning of the content body, truncated to a UI-friendly length
 * @param indexedAt       indexed timestamp (nullable when the field was not stored)
 */
public record DocumentSummary(
    String id,
    String namespaceId,
    String title,
    String snippet,
    Instant indexedAt
) {

    public DocumentSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        snippet = Objects.requireNonNullElse(snippet, "");
    }
}
