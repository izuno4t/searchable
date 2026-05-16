package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.chunking.ChunkingStrategy;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.chunking.WholeDocumentChunkingStrategy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Indexes domain documents into Lucene.
 *
 * <p>Documents are first passed through a {@link ChunkingStrategy} (defaulting
 * to {@link WholeDocumentChunkingStrategy} for backward compatibility); each
 * resulting {@link Chunk} is stored as its own Lucene sub-document keyed by
 * {@link LuceneFields#PARENT_ID}. When an {@link EmbeddingProvider} is
 * supplied the chunk text is embedded and stored in the KNN vector field.
 */
public final class LuceneIndexer {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexer.class);

    private final LuceneIndexProvider provider;
    private final LuceneDocumentMapper mapper;
    private final EmbeddingProvider embeddingProvider;
    private final ChunkingStrategy chunkingStrategy;

    public LuceneIndexer(final LuceneIndexProvider provider) {
        this(provider, new LuceneDocumentMapper(), null, new WholeDocumentChunkingStrategy());
    }

    public LuceneIndexer(final LuceneIndexProvider provider,
                         final EmbeddingProvider embeddingProvider) {
        this(provider, new LuceneDocumentMapper(), embeddingProvider,
            new WholeDocumentChunkingStrategy());
    }

    public LuceneIndexer(final LuceneIndexProvider provider,
                         final LuceneDocumentMapper mapper,
                         final EmbeddingProvider embeddingProvider) {
        this(provider, mapper, embeddingProvider, new WholeDocumentChunkingStrategy());
    }

    public LuceneIndexer(final LuceneIndexProvider provider,
                         final LuceneDocumentMapper mapper,
                         final EmbeddingProvider embeddingProvider,
                         final ChunkingStrategy chunkingStrategy) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.embeddingProvider = embeddingProvider;
        this.chunkingStrategy = Objects.requireNonNull(chunkingStrategy,
            "chunkingStrategy must not be null");
    }

    /** Insert or replace a single document; visible after the next refresh. */
    public void index(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        final LuceneIndexContext ctx = provider.getOrCreate(document.namespaceId());
        try {
            final IndexWriter writer = ctx.writer();
            writeChunks(writer, document);
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
                writeChunks(writer, doc);
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

    /** Delete a single document and all its chunks; returns whether anything matched. */
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
            writer.deleteDocuments(new Term(LuceneFields.PARENT_ID, documentId));
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

    private void writeChunks(final IndexWriter writer, final Document doc) throws IOException {
        final List<Chunk> chunks = chunkingStrategy.chunk(doc);
        // Replace any prior chunks for this parent document.
        writer.deleteDocuments(new Term(LuceneFields.PARENT_ID, doc.id()));
        for (final Chunk chunk : chunks) {
            final float[] vector = embeddingProvider != null
                ? embeddingProvider.embed(chunk.text()) : null;
            writer.addDocument(mapper.toLucene(doc, chunk, vector));
        }
    }
}
