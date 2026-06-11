package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.search.PaginationParams;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneVectorSearcherTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private LuceneIndexer indexer;
    private LuceneVectorSearcher searcher;
    private EmbeddingProvider embedding;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir), AnalyzerFactory.japanese());
        embedding = new HashEmbeddingProvider(128);
        indexer = new LuceneIndexer(provider, embedding);
        searcher = new LuceneVectorSearcher(provider, embedding);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private Document doc(final String id, final String title, final String content) {
        return Document.builder().id(id).namespaceId("ns").title(title).content(content).build();
    }

    @Test
    void exactQueryReturnsIndexedDocumentAsTopHit() {
        indexer.indexBatch("ns", List.of(
            doc("d1", "Lucene入門", "全文検索エンジンの解説"),
            doc("d2", "形態素解析", "日本語のトークン化"),
            doc("d3", "ベクトル検索", "意味的類似度に基づく検索")
        ));

        final SearchResult result = searcher.search("ns",
            SearchRequest.builder().query("Lucene入門\n全文検索エンジンの解説").build());

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).documentId()).isEqualTo("d1");
    }

    @Test
    void paginationLimitsReturnedHits() {
        indexer.indexBatch("ns", List.of(
            doc("d1", "t1", "c1"),
            doc("d2", "t2", "c2"),
            doc("d3", "t3", "c3")
        ));

        final SearchResult result = searcher.search("ns",
            SearchRequest.builder()
                .query("anything")
                .pagination(new PaginationParams(0, 2))
                .build());

        assertThat(result.hits()).hasSize(2);
    }

    @Test
    void lazyLoadOmitsContent() {
        indexer.indexBatch("ns", List.of(
            doc("d1", "Lucene入門", "全文検索エンジンの解説")
        ));
        final SearchResult result = searcher.search("ns",
            SearchRequest.builder()
                .query("Lucene入門")
                .options(new io.searchable.core.domain.search.SearchOptions(true, 100, true, true))
                .build());
        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).content()).isNull();
    }

    @Test
    void searcherWorksOnEmptyNamespace() {
        provider.getOrCreate("ns");
        final SearchResult result = searcher.search("ns",
            SearchRequest.builder().query("nothing here").build());

        assertThat(result.hits()).isEmpty();
        assertThat(result.totalHits()).isZero();
    }
}
