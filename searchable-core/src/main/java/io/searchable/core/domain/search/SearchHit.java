package io.searchable.core.domain.search;

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
 * @param subResults  section-level matches inside this document
 *                    (TASK-049/050); the list is never {@code null} but may
 *                    be empty when chunking did not split the document or
 *                    no further sections matched
 */
public record SearchHit(
    String documentId,
    String namespaceId,
    String title,
    String content,
    double score,
    Map<String, List<String>> highlights,
    Map<String, Object> metadata,
    List<SubResult> subResults
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
        subResults = subResults == null ? List.of() : List.copyOf(subResults);
    }

    /** Backward-compatible constructor without {@code subResults}. */
    public SearchHit(final String documentId,
                     final String namespaceId,
                     final String title,
                     final String content,
                     final double score,
                     final Map<String, List<String>> highlights,
                     final Map<String, Object> metadata) {
        this(documentId, namespaceId, title, content, score,
            highlights, metadata, List.of());
    }
}
