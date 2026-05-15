package com.searchable.core.application;

import com.searchable.core.domain.document.Document;
import com.searchable.core.domain.embedding.EmbeddingProvider;
import com.searchable.core.domain.search.PaginationParams;
import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchRequest;
import com.searchable.core.domain.search.SearchResult;
import com.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import com.searchable.core.infrastructure.lucene.AnalyzerFactory;
import com.searchable.core.infrastructure.lucene.IndexLayout;
import com.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import com.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import com.searchable.core.infrastructure.lucene.LuceneIndexer;
import com.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchOrchestratorTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private LuceneIndexer indexer;
    private HybridSearchOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir), AnalyzerFactory.japanese());
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        indexer = new LuceneIndexer(provider, embedding);
        orchestrator = new HybridSearchOrchestrator(
            new LuceneFullTextSearcher(provider),
            new LuceneVectorSearcher(provider, embedding));

        indexer.indexBatch("ns", List.of(
            doc("d1", "Lucene 入門", "全文検索エンジンの解説"),
            doc("d2", "形態素解析", "日本語のトークン化"),
            doc("d3", "Lucene と日本語", "全文検索 と 日本語処理 を組み合わせる"),
            doc("d4", "ベクトル検索", "意味的類似度に基づく検索")
        ));
    }

    @AfterEach
    void tearDown() {
        orchestrator.close();
        provider.close();
    }

    private Document doc(final String id, final String title, final String content) {
        return Document.builder().id(id).namespaceId("ns").title(title).content(content).build();
    }

    @Test
    void parallelHybridReturnsUnionRanking() {
        final SearchResult result = orchestrator.parallel("ns",
            SearchRequest.builder()
                .query("全文検索")
                .pagination(new PaginationParams(0, 10))
                .build());

        // Full-text matches (d1, d3) appear in the merged ranking.
        assertThat(result.hits()).extracting(h -> h.documentId())
            .contains("d1", "d3");
    }

    @Test
    void sequentialFullTextFirstKeepsOnlyDocsInBothEngines() {
        final SearchResult result = orchestrator.sequential("ns",
            SearchRequest.builder()
                .query("全文検索")
                .pagination(new PaginationParams(0, 10))
                .build(),
            SearchOrder.FULL_TEXT_FIRST);

        // Full-text search matches d1, d3, d4 (each has 検索 in content);
        // vector search over 4 docs returns all of them, so the intersection
        // equals the full-text result set.
        assertThat(result.hits()).extracting(h -> h.documentId())
            .contains("d1", "d3");
        assertThat(result.hits()).doesNotContainNull();
    }

    @Test
    void paginationRestrictsReturnedHits() {
        final SearchResult result = orchestrator.parallel("ns",
            SearchRequest.builder()
                .query("検索")
                .pagination(new PaginationParams(0, 2))
                .build());

        assertThat(result.hits()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void sequentialVectorFirstAlsoReturnsIntersection() {
        final SearchResult result = orchestrator.sequential("ns",
            SearchRequest.builder()
                .query("全文検索")
                .pagination(new PaginationParams(0, 10))
                .build(),
            SearchOrder.VECTOR_FIRST);

        assertThat(result.hits()).extracting(h -> h.documentId()).contains("d1", "d3");
    }
}
