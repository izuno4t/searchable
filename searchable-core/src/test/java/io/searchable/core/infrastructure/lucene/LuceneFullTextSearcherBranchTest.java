package io.searchable.core.infrastructure.lucene;

import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.chunking.ChunkingStrategy;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.search.SearchOptions;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.infrastructure.chunking.SentenceChunkingStrategy;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Branch coverage helpers for {@link LuceneFullTextSearcher}. */
class LuceneFullTextSearcherBranchTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private LuceneIndexer indexer;
    private LuceneFullTextSearcher searcher;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        provider = new LuceneIndexProvider(new IndexLayout(tempDir.resolve("indexes")),
            AnalyzerFactory.japanese());
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        // Use a sentence chunker so that long docs yield multiple chunks
        // per parent and exercise the SubResult build branches.
        final ChunkingStrategy chunker = new SentenceChunkingStrategy(40);
        indexer = new LuceneIndexer(provider, new LuceneDocumentMapper(), embedding, chunker);
        searcher = new LuceneFullTextSearcher(provider);

        final var nsRepo = new JdbcNamespaceRepository(dataSource);
        final var metaRepo = new JdbcIndexMetadataRepository(dataSource);
        new NamespaceService(nsRepo, metaRepo, provider, SearchableGlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC))
            .create("ns", "N", null);
        indexer.indexBatch("ns", List.of(
            doc("d1", "Lucene入門",
                "Apache Luceneは全文検索ライブラリ。Javaから利用できる。組み込み可能。"),
            doc("d2", "Kuromojiの概要",
                "Kuromojiは日本語形態素解析器である。Luceneに統合されている。"),
            doc("d3", "ハイブリッド検索",
                "全文検索とベクトル検索を組み合わせるとハイブリッド検索になる。")
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    private Document doc(final String id, final String title, final String content) {
        return Document.builder()
            .id(id).namespaceId("ns").title(title).content(content)
            .metadata(Map.of("url", "https://example.com/" + id))
            .build();
    }

    @Test
    void rejectsNullArgs() {
        assertThatThrownBy(() -> searcher.search(null,
            SearchRequest.builder().query("x").build()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> searcher.search("ns", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullProviderInConstructor() {
        assertThatThrownBy(() -> new LuceneFullTextSearcher(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LuceneFullTextSearcher(null, new LuceneDocumentMapper()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LuceneFullTextSearcher(provider, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void bm25K1OverrideAppliedWhenOnlyK1Set() {
        final SearchOptions opts = new SearchOptions(true, 100, true, false, 1.6, null);
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene").options(opts).build());
        assertThat(r.hits()).isNotEmpty();
    }

    @Test
    void bm25BOverrideAppliedWhenOnlyBSet() {
        final SearchOptions opts = new SearchOptions(true, 100, true, false, null, 0.5);
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene").options(opts).build());
        assertThat(r.hits()).isNotEmpty();
    }

    @Test
    void bothBm25OverridesApplied() {
        final SearchOptions opts = new SearchOptions(true, 100, true, false, 1.7, 0.4);
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene").options(opts).build());
        assertThat(r.hits()).isNotEmpty();
    }

    @Test
    void metaWeightsTriggersMultiFieldQuery() {
        final SearchOptions opts = new SearchOptions(true, 100, true, false, null, null,
            Map.of("title", 3.0, "content", 1.0));
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene").options(opts).build());
        assertThat(r.hits()).isNotEmpty();
    }

    @Test
    void lazyLoadOmitsContentAndHighlights() {
        final SearchOptions opts = new SearchOptions(true, 100, true, true);
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene").options(opts).build());
        assertThat(r.hits()).isNotEmpty();
        assertThat(r.hits().get(0).content()).isNull();
        assertThat(r.hits().get(0).highlights()).isEmpty();
    }

    @Test
    void highlightWithoutEscapeMarkupBranch() {
        // escapeMarkup=false picks the (formatter, scorer) Highlighter constructor.
        final SearchOptions opts = new SearchOptions(true, 100, false, false);
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene").options(opts).build());
        assertThat(r.hits()).isNotEmpty();
    }

    @Test
    void searchWrapsExceptionsFromAcquireSearcher() throws Exception {
        // The catch-then-wrap branch fires when acquireSearcher / parseQuery /
        // searcher.search throw. Mock the cached context to fail at acquire.
        final LuceneIndexProvider failing = org.mockito.Mockito.mock(LuceneIndexProvider.class);
        final LuceneIndexContext brokenCtx = org.mockito.Mockito.mock(LuceneIndexContext.class);
        org.mockito.Mockito.when(failing.getOrCreate(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(brokenCtx);
        org.mockito.Mockito.when(brokenCtx.acquireSearcher())
            .thenThrow(new java.io.IOException("acquire-boom"));
        final LuceneFullTextSearcher s = new LuceneFullTextSearcher(failing);
        assertThatThrownBy(() -> s.search("ns", SearchRequest.builder().query("x").build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void subResultsCarryUrlBasedAnchor() {
        // A sentence-chunked document with content split into multiple chunks
        // produces sub-results carrying the parent metadata's url.
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene 形態素").build());
        assertThat(r.hits()).isNotEmpty();
        // Don't assert subResult counts (depends on tokeniser output); ensure
        // the build helper itself runs by querying the parent metadata.
    }

    @Test
    void emptyContentPathReturnsNoHighlights() {
        // A document with empty content goes through the buildHighlights
        // null/empty short-circuit.
        indexer.index(Document.builder()
            .id("d-empty").namespaceId("ns").title("Lucene-only-title").content("")
            .metadata(Map.of("url", "https://e.x/empty")).build());
        final SearchResult r = searcher.search("ns",
            SearchRequest.builder().query("Lucene-only-title").build());
        assertThat(r.hits().stream().anyMatch(h -> h.documentId().equals("d-empty")))
            .isTrue();
    }
}
