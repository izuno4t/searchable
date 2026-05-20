package io.searchable.core.application;

import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.domain.document.DocumentMetadataRepository;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application-layer helper that enumerates indexed documents in a namespace.
 *
 * <p>Backed by {@link DocumentMetadataRepository} (one row per document) so
 * pagination, count, and ordering use plain SQL. Lucene is not queried
 * here — chunk-level duplication and {@code MatchAllDocsQuery} ordering
 * concerns from the previous implementation are gone.
 */
public final class DocumentBrowser {

    private final DocumentMetadataRepository repository;

    public DocumentBrowser(final DocumentMetadataRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * Backwards-compatible constructor for callers that have not yet
     * been migrated. Document listings issued through this overload
     * return empty pages because metadata is no longer stored in Lucene.
     * Callers should switch to the {@code DocumentMetadataRepository}
     * overload as part of the same release.
     *
     * @deprecated supply a {@link DocumentMetadataRepository} instead.
     */
    @Deprecated
    public DocumentBrowser(final LuceneIndexProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        this.repository = null;
    }

    /** Returns a page of documents in the namespace, newest first. */
    public DocumentPage list(final String namespaceId, final int offset, final int limit) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (repository == null) {
            return new DocumentPage(List.of(), 0L);
        }
        final long total = repository.count(namespaceId);
        if (total == 0L) {
            return new DocumentPage(List.of(), 0L);
        }
        final List<DocumentMetadataRecord> rows = repository.list(namespaceId, offset, limit);
        final List<DocumentSummary> items = new ArrayList<>(rows.size());
        for (final DocumentMetadataRecord row : rows) {
            items.add(toSummary(row));
        }
        return new DocumentPage(items, total);
    }

    /** Fetch a single document by id. */
    public Optional<DocumentSummary> findById(final String namespaceId, final String documentId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (repository == null) {
            return Optional.empty();
        }
        return repository.findById(namespaceId, documentId).map(this::toSummary);
    }

    private DocumentSummary toSummary(final DocumentMetadataRecord rec) {
        // Snippet generation now belongs to the search layer (which has
        // access to the per-chunk Lucene content). The browser surfaces
        // the authoritative document attributes only.
        return new DocumentSummary(
            rec.documentId(),
            rec.namespaceId(),
            rec.title(),
            "",
            rec.indexedAt());
    }
}
