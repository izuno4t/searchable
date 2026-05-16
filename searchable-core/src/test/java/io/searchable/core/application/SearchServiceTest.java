package io.searchable.core.application;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import io.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchServiceTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider indexProvider;
    private NamespaceService namespaceService;
    private SearchService searchService;
    private LuceneIndexer indexer;
    private HybridSearchOrchestrator hybrid;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        indexProvider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("indexes")),
            AnalyzerFactory.japanese());
        final JdbcNamespaceRepository namespaces = new JdbcNamespaceRepository(dataSource);
        namespaceService = new NamespaceService(
            namespaces,
            new JdbcIndexMetadataRepository(dataSource),
            indexProvider,
            GlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));

        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        indexer = new LuceneIndexer(indexProvider, embedding);
        final LuceneFullTextSearcher fullText = new LuceneFullTextSearcher(indexProvider);
        final LuceneVectorSearcher vector = new LuceneVectorSearcher(indexProvider, embedding);
        hybrid = new HybridSearchOrchestrator(fullText, vector);
        searchService = new SearchService(namespaces, fullText, vector, hybrid);
    }

    @AfterEach
    void tearDown() throws Exception {
        hybrid.close();
        indexProvider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    private Document doc(final String ns, final String id, final String title, final String content) {
        return Document.builder().id(id).namespaceId(ns).title(title).content(content).build();
    }

    @Test
    void searchTargetsSpecifiedNamespaceOnly() {
        namespaceService.create("ns-a", "A", null);
        namespaceService.create("ns-b", "B", null);
        indexer.index(doc("ns-a", "d1", "A-title", "全文検索エンジンの紹介"));
        indexer.index(doc("ns-b", "d2", "B-title", "ベクトル検索の紹介"));

        final SearchResult result = searchService.search(SearchRequest.builder()
            .query("全文")
            .namespaceIds(List.of("ns-a"))
            .build());

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).documentId()).isEqualTo("d1");
    }

    @Test
    void emptyNamespaceListSearchesAcrossAll() {
        namespaceService.create("ns-a", "A", null);
        namespaceService.create("ns-b", "B", null);
        indexer.index(doc("ns-a", "d1", "a", "Lucene 全文検索"));
        indexer.index(doc("ns-b", "d2", "b", "ベクトル 全文 検索"));

        final SearchResult result = searchService.search(SearchRequest.builder()
            .query("全文").build());

        assertThat(result.hits()).hasSize(2);
        assertThat(result.totalHits()).isEqualTo(2L);
    }

    @Test
    void unknownNamespaceThrows() {
        namespaceService.create("ns-a", "A", null);
        assertThatThrownBy(() -> searchService.search(SearchRequest.builder()
            .query("x").namespaceIds(List.of("missing")).build()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void vectorRequestRoutesToVectorSearcher() {
        namespaceService.create("ns-a", "A", null);
        indexer.index(doc("ns-a", "d1", "Lucene", "全文検索の解説"));

        final SearchResult result = searchService.search(SearchRequest.builder()
            .query("全文検索の解説")
            .namespaceIds(List.of("ns-a"))
            .searchType(SearchType.VECTOR)
            .build());

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).documentId()).isEqualTo("d1");
    }

    @Test
    void hybridRequestRoutesToOrchestrator() {
        namespaceService.create("ns-a", "A", new NamespaceConfigPatch(
            SearchType.HYBRID, SearchStrategy.PARALLEL, null, null, null, null));
        indexer.index(doc("ns-a", "d1", "全文検索", "Lucene engine"));

        final SearchResult result = searchService.search(SearchRequest.builder()
            .query("全文検索 Lucene")
            .namespaceIds(List.of("ns-a"))
            .searchType(SearchType.HYBRID)
            .build());

        assertThat(result.hits()).isNotEmpty();
    }

    @Test
    void bm25OverrideChangesScoring() {
        // BM25's b parameter controls length normalization; with documents of
        // unequal length the b value visibly changes the ranking score for
        // the shorter / longer doc.
        namespaceService.create("ns-bm25", "B", null);
        indexer.index(doc("ns-bm25", "short", "全文検索", "全文検索"));
        indexer.index(doc("ns-bm25", "long", "全文検索",
            "全文検索 補助 補助 補助 補助 補助 補助 補助 補助 補助 補助 補助 補助 補助 補助"));

        final SearchResult defaultScored = searchService.search(SearchRequest.builder()
            .query("全文検索")
            .namespaceIds(List.of("ns-bm25"))
            .build());
        final SearchResult noLengthNorm = searchService.search(SearchRequest.builder()
            .query("全文検索")
            .namespaceIds(List.of("ns-bm25"))
            .options(new io.searchable.core.domain.search.SearchOptions(
                true, 200, true, false, null, 0.0))
            .build());

        final double shortDefault = defaultScored.hits().stream()
            .filter(h -> h.documentId().equals("short"))
            .findFirst().orElseThrow().score();
        final double shortNoNorm = noLengthNorm.hits().stream()
            .filter(h -> h.documentId().equals("short"))
            .findFirst().orElseThrow().score();
        // With b=0.0 the short document loses its length-normalization bonus,
        // so its score drops compared to the default (b=0.75).
        assertThat(shortNoNorm).isNotEqualTo(shortDefault);
    }

    @Test
    void indexWeightScalesScoresDuringCrossNamespaceAggregation() {
        // Two namespaces with identical content: the one with higher
        // indexWeight wins in the merged ranking.
        namespaceService.create("ns-low", "Low",
            new NamespaceConfigPatch(null, null, null, null, null, 0.5, null));
        namespaceService.create("ns-high", "High",
            new NamespaceConfigPatch(null, null, null, null, null, 3.0, null));
        indexer.index(doc("ns-low", "d-low", "全文検索", "全文検索の解説"));
        indexer.index(doc("ns-high", "d-high", "全文検索", "全文検索の解説"));

        final SearchResult result = searchService.search(SearchRequest.builder()
            .query("全文検索")
            .namespaceIds(List.of("ns-low", "ns-high"))
            .build());

        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).documentId()).isEqualTo("d-high");
        assertThat(result.hits().get(1).documentId()).isEqualTo("d-low");
        assertThat(result.hits().get(0).score())
            .isGreaterThan(result.hits().get(1).score());
    }

    @Test
    void implicitTypeFromNamespaceConfig() {
        namespaceService.create("ns-a", "A", new NamespaceConfigPatch(
            SearchType.VECTOR, null, null, null, null, null));
        indexer.index(doc("ns-a", "d1", "title", "本文"));

        final SearchResult result = searchService.search(SearchRequest.builder()
            .query("本文")
            .namespaceIds(List.of("ns-a"))
            .build());

        assertThat(result.hits()).isNotEmpty();
    }
}
