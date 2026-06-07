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
 * holds two related groups of fields:
 *
 * <ul>
 *   <li><b>User-facing attributes</b> ({@code title}, {@code metadata},
 *       {@code indexedAt}) — what a search response and a document-listing
 *       UI need.</li>
 *   <li><b>Provenance / change-detection state</b> ({@code source}) —
 *       where the document came from and the content hash captured at
 *       ingest time, used by {@code IndexService.indexIfChanged} to skip
 *       documents that have not changed.</li>
 * </ul>
 *
 * <p>The two groups were unified after both used the same composite key
 * and the previous split left orphaned source rows after {@code delete()}
 * / {@code rebuild()}, causing change detection to wrongly skip
 * re-ingestion.
 *
 * @param namespaceId namespace identifier (FK to {@code NAMESPACE.ID})
 * @param documentId  document identifier unique within the namespace
 * @param title       human-readable document title (must not be null;
 *                    pass an empty string if the source has no title)
 * @param metadata    free-form metadata map; reserved keys
 *                    ({@code url}, {@code contentType}, {@code category},
 *                    {@code lang}, {@code tags}) follow the rules
 *                    documented in {@code docs/devel/design/architecture/overview.md} §5.7
 * @param indexedAt   timestamp of the last successful indexing of the
 *                    document; never null
 * @param source      origin descriptor (type / location / contentHash /
 *                    sourceUpdated) used for change detection; may be
 *                    {@code null} when the document was supplied inline
 *                    without provenance metadata
 */
public record DocumentMetadataRecord(
    String namespaceId,
    String documentId,
    String title,
    Map<String, Object> metadata,
    Instant indexedAt,
    DocumentSource source
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

    /** Backwards-compatible 5-arg constructor (no source attached). */
    public DocumentMetadataRecord(final String namespaceId,
                                  final String documentId,
                                  final String title,
                                  final Map<String, Object> metadata,
                                  final Instant indexedAt) {
        this(namespaceId, documentId, title, metadata, indexedAt, null);
    }
}
