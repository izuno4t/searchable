package io.searchable.core.domain.document;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative per-document record stored in the metadata DB.
 *
 * <p>Keyed by the natural composite key {@code (namespaceId, documentId)} —
 * the project deliberately does not introduce a surrogate id. The record
 * holds document-level attributes (title, free-form metadata, indexed-at
 * timestamp) that used to live as Lucene stored fields on every chunk.
 * Holding them once in the DB lets the Lucene index stay focused on
 * search-time data (tokens, vectors, chunk-specific fields) and lets
 * document listings / facet aggregation be answered by SQL.
 *
 * @param namespaceId namespace identifier (FK to {@code NAMESPACE.ID})
 * @param documentId  document identifier unique within the namespace
 * @param title       human-readable document title (must not be null;
 *                    pass an empty string if the source has no title)
 * @param metadata    free-form metadata map; reserved keys
 *                    ({@code url}, {@code category}, {@code lang},
 *                    {@code tags}) follow the rules documented in
 *                    {@code docs/architecture.md} §5.7
 * @param indexedAt   timestamp of the last successful indexing of the
 *                    document; never null
 */
public record DocumentMetadataRecord(
    String namespaceId,
    String documentId,
    String title,
    Map<String, Object> metadata,
    Instant indexedAt
) {

    public DocumentMetadataRecord {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(indexedAt, "indexedAt must not be null");
        if (namespaceId.isBlank()) {
            throw new IllegalArgumentException("namespaceId must not be blank");
        }
        if (documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
