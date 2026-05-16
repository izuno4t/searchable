package io.searchable.core.domain.document;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link DocumentSource} rows used by the change-detection
 * pipeline.
 *
 * <p>Each {@code (namespaceId, documentId)} pair has at most one source
 * record. {@link #findByDocumentId(String, String)} returns the latest
 * known content hash and source timestamp so callers can decide whether
 * a fresh ingest is necessary.
 */
public interface DocumentSourceRepository {

    /** Upsert the source record for {@code documentId} in {@code namespaceId}. */
    void save(String namespaceId, String documentId, DocumentSource source);

    Optional<DocumentSource> findByDocumentId(String namespaceId, String documentId);

    /** All source records belonging to the namespace. */
    List<DocumentSource> findByNamespace(String namespaceId);

    /** Remove the source record; returns whether a row existed. */
    boolean delete(String namespaceId, String documentId);
}
