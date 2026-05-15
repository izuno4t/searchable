package com.searchable.core.domain.search;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single search hit.
 *
 * @param documentId  document identifier
 * @param namespaceId namespace the document belongs to
 * @param title       document title (stored field)
 * @param content     document content (stored field, may be null when lazy-loading)
 * @param score       relevance score (higher = more relevant)
 * @param highlights  highlighted fragments per field (may be empty)
 * @param metadata    metadata included in the index (may be empty)
 */
public record SearchHit(
    String documentId,
    String namespaceId,
    String title,
    String content,
    double score,
    Map<String, List<String>> highlights,
    Map<String, Object> metadata
) {

    public SearchHit {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        highlights = highlights == null
            ? Map.of()
            : Map.copyOf(highlights);
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
