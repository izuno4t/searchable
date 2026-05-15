package com.searchable.core.infrastructure.lucene;

import com.searchable.core.domain.embedding.EmbeddingProvider;
import com.searchable.core.domain.search.SearchHit;
import com.searchable.core.domain.search.SearchRequest;
import com.searchable.core.domain.search.SearchResult;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes nearest-neighbor vector search against a namespace's Lucene index.
 *
 * <p>The query text is embedded via the supplied {@link EmbeddingProvider},
 * then a {@code KnnFloatVectorQuery} is run with {@code k} derived from
 * {@code offset + limit} so that pagination can be applied client-side.
 */
public final class LuceneVectorSearcher {

    private final LuceneIndexProvider provider;
    private final LuceneDocumentMapper mapper;
    private final EmbeddingProvider embeddingProvider;

    public LuceneVectorSearcher(final LuceneIndexProvider provider,
                                final EmbeddingProvider embeddingProvider) {
        this(provider, new LuceneDocumentMapper(), embeddingProvider);
    }

    public LuceneVectorSearcher(final LuceneIndexProvider provider,
                                final LuceneDocumentMapper mapper,
                                final EmbeddingProvider embeddingProvider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider,
            "embeddingProvider must not be null");
    }

    public SearchResult search(final String namespaceId, final SearchRequest request) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        final long start = System.nanoTime();

        final LuceneIndexContext ctx = provider.getOrCreate(namespaceId);
        IndexSearcher searcher = null;
        try {
            searcher = ctx.acquireSearcher();
            final float[] queryVector = embeddingProvider.embed(request.query());
            final int topN = Math.max(
                request.pagination().offset() + request.pagination().limit(), 1);
            final KnnFloatVectorQuery query = new KnnFloatVectorQuery(
                LuceneFields.VECTOR, queryVector, topN);
            final TopDocs hits = searcher.search(query, topN);

            final List<SearchHit> result = collectHits(searcher, hits, request, namespaceId);
            final double maxScore = hits.scoreDocs.length == 0 ? 0.0 : hits.scoreDocs[0].score;
            final long tookMs = (System.nanoTime() - start) / 1_000_000;
            return new SearchResult(result, hits.totalHits.value(), maxScore, Map.of(), tookMs);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to execute vector search on namespace " + namespaceId, e);
        } finally {
            try {
                if (searcher != null) {
                    ctx.release(searcher);
                }
            } catch (IOException ignored) {
                // best-effort release
            }
        }
    }

    private List<SearchHit> collectHits(final IndexSearcher searcher,
                                        final TopDocs hits,
                                        final SearchRequest request,
                                        final String namespaceId) throws IOException {
        final List<SearchHit> result = new ArrayList<>();
        final int offset = request.pagination().offset();
        final int upper = Math.min(hits.scoreDocs.length,
            offset + request.pagination().limit());

        for (int i = offset; i < upper; i++) {
            final ScoreDoc scoreDoc = hits.scoreDocs[i];
            final org.apache.lucene.document.Document doc =
                searcher.storedFields().document(scoreDoc.doc);
            final String parentId = doc.get(LuceneFields.PARENT_ID);
            final String id = parentId != null ? parentId : doc.get(LuceneFields.ID);
            final String title = doc.get(LuceneFields.TITLE);
            final String content = doc.get(LuceneFields.CONTENT);
            final Map<String, Object> metadata = mapper.deserializeMetadata(
                doc.get(LuceneFields.METADATA_JSON));

            result.add(new SearchHit(id, namespaceId, title, content, scoreDoc.score,
                Map.of(), metadata));
        }
        return result;
    }
}
