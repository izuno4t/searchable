package io.searchable.core.domain.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the authoritative document registry.
 *
 * <p>Holds one row per indexed document keyed by
 * {@code (namespaceId, documentId)}. This is the canonical store for
 * document-level metadata (title, free-form {@code Document.metadata},
 * indexed-at timestamp). Lucene is treated as a derived search index
 * over the chunks of these documents and does not duplicate this
 * metadata onto every chunk.
 *
 * <p>Distinct from {@link DocumentSourceRepository} which records
 * change-detection state (content hash, source location / type).
 * Splitting the two keeps "what is this document" separate from
 * "where did we last fetch it from".
 *
 * <p>See {@code docs/devel/design/architecture/overview.md} §5.7 for the full design.
 */
public interface DocumentMetadataRepository {

    /**
     * Insert or replace the document registry row.
     *
     * @param record the document metadata record to persist
     */
    void save(DocumentMetadataRecord record);

    /**
     * Look up a single document by its natural key.
     *
     * @param namespaceId owning namespace
     * @param documentId  document identifier within the namespace
     */
    Optional<DocumentMetadataRecord> findById(String namespaceId, String documentId);

    /**
     * Batch lookup within a single namespace. Used by the search-time
     * enricher to fetch metadata for all hits in one round-trip.
     *
     * @param namespaceId owning namespace
     * @param documentIds set of document identifiers to fetch (may be empty)
     * @return records for the requested ids; missing ids are simply absent
     *         from the result. Ordering is not guaranteed.
     */
    List<DocumentMetadataRecord> findByIds(String namespaceId, Collection<String> documentIds);

    /**
     * Paged listing for the admin / browse UI.
     *
     * @param namespaceId owning namespace
     * @param offset      zero-based offset
     * @param limit       maximum number of rows to return (must be positive)
     */
    List<DocumentMetadataRecord> list(String namespaceId, int offset, int limit);

    /** Total number of documents registered in the namespace. */
    long count(String namespaceId);

    /**
     * Remove a single document row. Returns whether a row existed.
     */
    boolean delete(String namespaceId, String documentId);

    /**
     * Remove every document row for the namespace. Used when a namespace
     * is wiped via {@code IndexService.deleteAll}.
     */
    void deleteByNamespace(String namespaceId);
}
