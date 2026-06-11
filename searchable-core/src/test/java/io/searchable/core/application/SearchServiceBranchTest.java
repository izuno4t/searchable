package io.searchable.core.application;

import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.domain.search.SearchOptions;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchServiceBranchTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private SearchService searchService;
    private NamespaceService namespaceService;
    private LuceneIndexer indexer;
    private HybridSearchOrchestrator hybrid;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        provider = new LuceneIndexProvider(new IndexLayout(tempDir.resolve("indexes")),
            AnalyzerFactory.japanese());
        final var namespaces = new JdbcNamespaceRepository(dataSource);
        final var metadata = new JdbcIndexMetadataRepository(dataSource);
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        indexer = new LuceneIndexer(provider, embedding);
        final LuceneFullTextSearcher fullText = new LuceneFullTextSearcher(provider);
        final LuceneVectorSearcher vector = new LuceneVectorSearcher(provider, embedding);
        hybrid = new HybridSearchOrchestrator(fullText, vector);
        searchService = new SearchService(namespaces, fullText, vector, hybrid);
        namespaceService = new NamespaceService(namespaces, metadata, provider,
            SearchableGlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        hybrid.close();
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    private Document doc(final String ns, final String id, final String title, final String body) {
        return Document.builder().id(id).namespaceId(ns).title(title).content(body)
            .indexedAt(Instant.now()).build();
    }

    @Test
    void emptyTargetsYieldEmptyResult() {
        final SearchResult r = searchService.search(SearchRequest.builder()
            .query("x").namespaceIds(List.of()).build());
        assertThat(r.totalHits()).isZero();
        assertThat(r.hits()).isEmpty();
    }

    @Test
    void weightOneSkipsRescoringEvenWithHits() {
        namespaceService.create("ns_one", "One", null); // default weight = 1.0
        indexer.index(doc("ns_one", "d", "全文検索", "本文"));
        final SearchResult r = searchService.search(SearchRequest.builder()
            .query("全文検索").namespaceIds(List.of("ns_one")).build());
        assertThat(r.hits()).isNotEmpty();
    }

    @Test
    void weightAppliedWithEmptyHits() {
        // weight != 1.0 but no hits returned -> still uses fast path.
        namespaceService.create("ns_w", "W",
            new NamespaceConfigPatch(null, null, null, null, null, 2.0, null));
        // Don't index anything; search for term that won't match.
        final SearchResult r = searchService.search(SearchRequest.builder()
            .query("no-such-term-zzz").namespaceIds(List.of("ns_w")).build());
        assertThat(r.hits()).isEmpty();
    }

    @Test
    void hybridSequentialBranchIsReachable() {
        namespaceService.create("ns_seq", "Seq",
            new NamespaceConfigPatch(SearchType.HYBRID, SearchStrategy.SEQUENTIAL,
                null, null, null, null));
        indexer.index(doc("ns_seq", "d", "全文検索", "Lucene engine"));
        final SearchResult r = searchService.search(SearchRequest.builder()
            .query("全文検索 Lucene").namespaceIds(List.of("ns_seq"))
            .searchType(SearchType.HYBRID).build());
        assertThat(r.hits()).isNotEmpty();
    }

    @Test
    void hybridSearchForMissingNamespaceThrows() {
        assertThatThrownBy(() -> searchService.search(SearchRequest.builder()
                .query("x").namespaceIds(List.of("never-existed"))
                .searchType(SearchType.HYBRID).build()))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void fanOutAcrossTwoEmptyNamespacesProducesEmptyResult() {
        // Two namespaces, neither has any indexed documents. The aggregate
        // path hits targets.size() > 1, walks both, and ends with
        // all.isEmpty()==true so maxScore=0 is returned via the true branch.
        namespaceService.create("ns_e1", "E1", null);
        namespaceService.create("ns_e2", "E2", null);
        final SearchResult r = searchService.search(SearchRequest.builder()
            .query("nothing-matches")
            .namespaceIds(List.of("ns_e1", "ns_e2")).build());
        assertThat(r.hits()).isEmpty();
        assertThat(r.totalHits()).isZero();
    }
}
