package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.search.PaginationParams;
import io.searchable.core.domain.search.SearchOptions;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneFullTextSearcherTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private LuceneIndexer indexer;
    private LuceneFullTextSearcher searcher;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(new IndexLayout(tempDir), AnalyzerFactory.japanese());
        indexer = new LuceneIndexer(provider);
        searcher = new LuceneFullTextSearcher(provider);
        indexer.indexBatch("ns", List.of(
            doc("d1", "Lucene入門",
                "Apache Luceneは組み込み可能な全文検索ライブラリで、Javaから利用できる。"),
            doc("d2", "形態素解析の概要",
                "形態素解析は日本語のテキストを単語単位に分割する処理である。Kuromojiが広く利用される。"),
            doc("d3", "ハイブリッド検索",
                "全文検索とベクトル検索を組み合わせるとハイブリッド検索になる。")
        ));
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private Document doc(final String id, final String title, final String content) {
        return Document.builder()
            .id(id).namespaceId("ns").title(title).content(content)
            .metadata(Map.of("category", "test"))
            .build();
    }

    @Test
    void searchByJapaneseTokenFindsExpectedDocument() {
        final SearchResult result = searcher.search("ns",
            SearchRequest.builder().query("形態素解析").build());

        assertThat(result.totalHits()).isGreaterThanOrEqualTo(1L);
        assertThat(result.hits()).anyMatch(h -> h.documentId().equals("d2"));
        assertThat(result.tookMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void highlightFragmentWrapsMatchedTerm() {
        final SearchResult result = searcher.search("ns",
            SearchRequest.builder().query("Kuromoji").build());

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).highlights().get("content"))
            .isNotNull()
            .anySatisfy(snippet -> assertThat(snippet).contains("<mark>"));
    }

    @Test
    void highlightDisabledWhenOptionFalse() {
        final SearchResult result = searcher.search("ns",
            SearchRequest.builder()
                .query("検索")
                .options(new SearchOptions(false))
                .build());

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).highlights()).isEmpty();
    }

    @Test
    void paginationRestrictsHitWindow() {
        final SearchResult page1 = searcher.search("ns",
            SearchRequest.builder().query("検索")
                .pagination(new PaginationParams(0, 1)).build());
        final SearchResult page2 = searcher.search("ns",
            SearchRequest.builder().query("検索")
                .pagination(new PaginationParams(1, 1)).build());

        assertThat(page1.hits()).hasSize(1);
        assertThat(page2.hits()).hasSize(1);
        assertThat(page1.hits().get(0).documentId())
            .isNotEqualTo(page2.hits().get(0).documentId());
    }

    @Test
    void emptyResultWhenNoMatch() {
        final SearchResult result = searcher.search("ns",
            SearchRequest.builder().query("存在しないキーワード").build());

        assertThat(result.hits()).isEmpty();
        assertThat(result.totalHits()).isZero();
        assertThat(result.maxScore()).isZero();
    }
}
