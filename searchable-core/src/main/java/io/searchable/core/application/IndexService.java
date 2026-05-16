package io.searchable.core.application;

import io.searchable.core.domain.document.Document;
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
    private final Clock clock;

    public IndexService(final NamespaceRepository namespaces,
                        final IndexMetadataRepository indexMetadata,
                        final LuceneIndexProvider indexProvider,
                        final LuceneIndexer indexer,
                        final Clock clock) {
        this.namespaces = Objects.requireNonNull(namespaces);
        this.indexMetadata = Objects.requireNonNull(indexMetadata);
        this.indexProvider = Objects.requireNonNull(indexProvider);
        this.indexer = Objects.requireNonNull(indexer);
        this.clock = Objects.requireNonNull(clock);
    }

    public void index(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        requireNamespaceExists(document.namespaceId());
        indexer.index(document);
        refreshMetadata(document.namespaceId(), IndexStatus.READY);
    }

    public void indexBatch(final String namespaceId, final List<Document> documents) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documents, "documents must not be null");
        requireNamespaceExists(namespaceId);

        markStatus(namespaceId, IndexStatus.INDEXING);
        try {
            indexer.indexBatch(namespaceId, documents);
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
            refreshMetadata(namespaceId, IndexStatus.READY);
        }
        return removed;
    }

    public void rebuild(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        requireNamespaceExists(namespaceId);
        markStatus(namespaceId, IndexStatus.INDEXING);
        indexer.deleteAll(namespaceId);
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
