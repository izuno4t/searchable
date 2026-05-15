package com.searchable.core.infrastructure.lucene;

import com.searchable.core.domain.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Performs index create / update / delete operations on a namespace's Lucene index.
 */
public final class LuceneIndexer {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexer.class);

    private final LuceneIndexProvider provider;
    private final LuceneDocumentMapper mapper;

    public LuceneIndexer(final LuceneIndexProvider provider) {
        this(provider, new LuceneDocumentMapper());
    }

    public LuceneIndexer(final LuceneIndexProvider provider, final LuceneDocumentMapper mapper) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /** Insert or replace a single document; visible after the next refresh. */
    public void index(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        final LuceneIndexContext ctx = provider.getOrCreate(document.namespaceId());
        try {
            final IndexWriter writer = ctx.writer();
            writer.updateDocument(new Term(LuceneFields.ID, document.id()),
                mapper.toLucene(document));
            writer.commit();
            ctx.refresh();
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to index document " + document.id(), e);
        }
    }

    /** Insert or replace a batch of documents; single commit at the end. */
    public void indexBatch(final String namespaceId, final Iterable<Document> documents) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documents, "documents must not be null");
        final LuceneIndexContext ctx = provider.getOrCreate(namespaceId);
        try {
            final IndexWriter writer = ctx.writer();
            int count = 0;
            for (final Document doc : documents) {
                if (!namespaceId.equals(doc.namespaceId())) {
                    throw new IllegalArgumentException(
                        "Document " + doc.id() + " does not belong to namespace " + namespaceId);
                }
                writer.updateDocument(new Term(LuceneFields.ID, doc.id()), mapper.toLucene(doc));
                count++;
            }
            writer.commit();
            ctx.refresh();
            log.info("indexed {} documents into namespace {}", count, namespaceId);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to index batch into namespace " + namespaceId, e);
        }
    }

    /** Delete a single document; returns whether anything matched. */
    public boolean delete(final String namespaceId, final String documentId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (!provider.isOpen(namespaceId)) {
            return false;
        }
        final LuceneIndexContext ctx = provider.getOrCreate(namespaceId);
        try {
            final IndexWriter writer = ctx.writer();
            final long before = ctx.documentCount();
            writer.deleteDocuments(new Term(LuceneFields.ID, documentId));
            writer.commit();
            ctx.refresh();
            return ctx.documentCount() < before;
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to delete document " + documentId, e);
        }
    }

    /** Drop all documents in a namespace (keeps the index open). */
    public void deleteAll(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        final LuceneIndexContext ctx = provider.getOrCreate(namespaceId);
        try {
            ctx.writer().deleteAll();
            ctx.writer().commit();
            ctx.refresh();
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to clear namespace " + namespaceId, e);
        }
    }
}
