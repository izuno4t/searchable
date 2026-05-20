package io.searchable.core.application;

import io.searchable.core.domain.document.ContentHashes;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.domain.document.DocumentMetadataRepository;
import io.searchable.core.domain.document.DocumentSource;
import io.searchable.core.domain.document.DocumentSourceRepository;
import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexMetadataRepository;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.domain.namespace.NamespaceRepository;
import io.searchable.core.infrastructure.lucene.LuceneIndexContext;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Application-layer service for index lifecycle operations.
 *
 * <p>Wraps the low-level {@link LuceneIndexer} and keeps the namespace's
 * {@link IndexMetadata} row in sync after each mutation.
 */
public final class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    private final NamespaceRepository namespaces;
    private final IndexMetadataRepository indexMetadata;
    private final LuceneIndexProvider indexProvider;
    private final LuceneIndexer indexer;
    private final DocumentSourceRepository documentSources;
    private final DocumentMetadataRepository documentMetadata;
    private final Clock clock;

    public IndexService(final NamespaceRepository namespaces,
                        final IndexMetadataRepository indexMetadata,
                        final LuceneIndexProvider indexProvider,
                        final LuceneIndexer indexer,
                        final Clock clock) {
        this(namespaces, indexMetadata, indexProvider, indexer, null, null, clock);
    }

    public IndexService(final NamespaceRepository namespaces,
                        final IndexMetadataRepository indexMetadata,
                        final LuceneIndexProvider indexProvider,
                        final LuceneIndexer indexer,
                        final DocumentSourceRepository documentSources,
                        final Clock clock) {
        this(namespaces, indexMetadata, indexProvider, indexer, documentSources, null, clock);
    }

    public IndexService(final NamespaceRepository namespaces,
                        final IndexMetadataRepository indexMetadata,
                        final LuceneIndexProvider indexProvider,
                        final LuceneIndexer indexer,
                        final DocumentSourceRepository documentSources,
                        final DocumentMetadataRepository documentMetadata,
                        final Clock clock) {
        this.namespaces = Objects.requireNonNull(namespaces);
        this.indexMetadata = Objects.requireNonNull(indexMetadata);
        this.indexProvider = Objects.requireNonNull(indexProvider);
        this.indexer = Objects.requireNonNull(indexer);
        this.documentSources = documentSources;
        this.documentMetadata = documentMetadata;
        this.clock = Objects.requireNonNull(clock);
    }

    public void index(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        requireNamespaceExists(document.namespaceId());
        indexer.index(document);
        recordSource(document);
        recordMetadata(document);
        refreshMetadata(document.namespaceId(), IndexStatus.READY);
    }

    /**
     * Index the document only if its content has changed since the last
     * ingest. Change detection compares the SHA-256 content hash supplied
     * via {@code document.source().contentHash()} (or computed on the fly)
     * against the value stored by the {@link DocumentSourceRepository}.
     *
     * @return {@code true} when the document was actually written to the index
     */
    public boolean indexIfChanged(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        requireNamespaceExists(document.namespaceId());
        if (documentSources == null) {
            // No repository configured -- behave like {@link #index(Document)}.
            index(document);
            return true;
        }
        final String newHash = effectiveHash(document);
        final Optional<DocumentSource> previous = documentSources.findByDocumentId(
            document.namespaceId(), document.id());
        if (previous.isPresent()
            && previous.get().contentHash() != null
            && previous.get().contentHash().equals(newHash)) {
            log.debug("skipping unchanged document {} (hash={})", document.id(), newHash);
            return false;
        }
        indexer.index(document);
        recordSource(document, newHash);
        recordMetadata(document);
        refreshMetadata(document.namespaceId(), IndexStatus.READY);
        return true;
    }

    private String effectiveHash(final Document document) {
        if (document.source() != null && document.source().contentHash() != null) {
            return document.source().contentHash();
        }
        return ContentHashes.hash(document);
    }

    private void recordSource(final Document document) {
        if (documentSources == null) {
            return;
        }
        recordSource(document, effectiveHash(document));
    }

    private void recordSource(final Document document, final String hash) {
        if (documentSources == null) {
            return;
        }
        final DocumentSource existing = document.source();
        final DocumentSource source = existing == null
            ? new DocumentSource("inline", document.id(), hash, null)
            : new DocumentSource(existing.type(), existing.location(), hash,
                existing.sourceUpdated());
        documentSources.save(document.namespaceId(), document.id(), source);
    }

    private void recordMetadata(final Document document) {
        if (documentMetadata == null) {
            return;
        }
        final Instant indexedAt = document.indexedAt() == null
            ? clock.instant()
            : document.indexedAt();
        documentMetadata.save(new DocumentMetadataRecord(
            document.namespaceId(),
            document.id(),
            document.title(),
            document.metadata(),
            indexedAt));
    }

    public void indexBatch(final String namespaceId, final List<Document> documents) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documents, "documents must not be null");
        requireNamespaceExists(namespaceId);

        markStatus(namespaceId, IndexStatus.INDEXING);
        try {
            indexer.indexBatch(namespaceId, documents);
            for (final Document d : documents) {
                recordMetadata(d);
            }
            refreshMetadata(namespaceId, IndexStatus.READY);
        } catch (RuntimeException e) {
            markStatus(namespaceId, IndexStatus.ERROR);
            throw e;
        }
    }

    public boolean delete(final String namespaceId, final String documentId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        requireNamespaceExists(namespaceId);
        final boolean removed = indexer.delete(namespaceId, documentId);
        if (removed) {
            if (documentMetadata != null) {
                documentMetadata.delete(namespaceId, documentId);
            }
            refreshMetadata(namespaceId, IndexStatus.READY);
        }
        return removed;
    }

    public void rebuild(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        requireNamespaceExists(namespaceId);
        markStatus(namespaceId, IndexStatus.INDEXING);
        indexer.deleteAll(namespaceId);
        if (documentMetadata != null) {
            documentMetadata.deleteByNamespace(namespaceId);
        }
        refreshMetadata(namespaceId, IndexStatus.READY);
        log.info("rebuilt index for namespace {}", namespaceId);
    }

    public IndexMetadata getMetadata(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        return indexMetadata.findByNamespaceId(namespaceId)
            .orElseThrow(() -> new NoSuchElementException(
                "No index metadata for namespace: " + namespaceId));
    }

    private void requireNamespaceExists(final String id) {
        if (!namespaces.exists(id)) {
            throw new NoSuchElementException("Namespace not found: " + id);
        }
    }

    private void markStatus(final String namespaceId, final IndexStatus status) {
        final IndexMetadata current = indexMetadata.findByNamespaceId(namespaceId)
            .orElse(IndexMetadata.empty(namespaceId, clock.instant()));
        indexMetadata.save(new IndexMetadata(
            namespaceId,
            current.documentCount(),
            current.indexSizeBytes(),
            clock.instant(),
            status,
            current.statistics()
        ));
    }

    private void refreshMetadata(final String namespaceId, final IndexStatus status) {
        try {
            final LuceneIndexContext ctx = indexProvider.getOrCreate(namespaceId);
            ctx.refresh();
            indexMetadata.save(new IndexMetadata(
                namespaceId,
                ctx.documentCount(),
                ctx.indexSizeBytes(),
                clock.instant(),
                status,
                Map.of()
            ));
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to refresh metadata for " + namespaceId, e);
        }
    }

    /** Used for {@code now()} in tests. */
    Instant clockInstant() {
        return clock.instant();
    }
}
