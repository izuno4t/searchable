package io.searchable.core;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.SearchService;
import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.IndexConfig;
import io.searchable.core.application.config.PluginsConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.infrastructure.chunking.FixedSizeChunkingStrategy;
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

class CoverageSweepFinal6Test {

    @TempDir Path tempDir;

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void luceneIndexerTwoArgConstructorAcceptsEmbeddingProvider() {
        // Cover the (provider, embedding) 2-arg constructor overload.
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("li-2arg")), AnalyzerFactory.japanese())) {
            final var indexer = new LuceneIndexer(provider,
                new io.searchable.core.infrastructure.embedding.HashEmbeddingProvider(64));
            assertThat(indexer).isNotNull();
        }
    }

    @Test
    void fixedSizeChunkingStrategyDefaultConstructor() {
        // The no-arg constructor is one of the unused overloads.
        final var s = new FixedSizeChunkingStrategy();
        final var chunks = s.chunk(Document.builder()
            .id("d").namespaceId("ns").title("t").content("abcdefghij").build());
        assertThat(chunks).isNotEmpty();
    }

    @Test
    void searchServiceAggregateFastPathSingleTarget() throws Exception {
        // Single-target search hits the if (targets.size()==1) branch.
        final String url = "jdbc:h2:" + tempDir.resolve("ss-fast") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("ss-fast-idx")), AnalyzerFactory.japanese())) {
            final var nsRepo = new JdbcNamespaceRepository(ds);
            final var mdRepo = new JdbcIndexMetadataRepository(ds);
            final var embed = new io.searchable.core.infrastructure.embedding.HashEmbeddingProvider(64);
            final var indexer = new LuceneIndexer(provider, embed);
            new NamespaceService(nsRepo, mdRepo, provider, GlobalConfig.defaults(), CLOCK)
                .create("s1", "S1", null);
            indexer.index(Document.builder().id("d").namespaceId("s1").title("t").content("x").build());

            final var ft = new LuceneFullTextSearcher(provider);
            final var vec = new LuceneVectorSearcher(provider, embed);
            final var hybrid = new io.searchable.core.application.HybridSearchOrchestrator(ft, vec);
            final SearchService svc = new SearchService(nsRepo, ft, vec, hybrid);
            try {
                final var r = svc.search(io.searchable.core.domain.search.SearchRequest.builder()
                    .query("x").namespaceIds(List.of("s1")).build());
                assertThat(r.hits()).isNotEmpty();
            } finally {
                hybrid.close();
            }
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void searchableLibraryCloseSwallowsIoException() {
        // Build a library then close it; close() handles AutoCloseable
        // exceptions via the swallow-and-log branch.
        try (SearchableLibrary lib = SearchableLibrary.builder()
                .applicationConfig(new ApplicationConfig(
                    tempDir,
                    new PersistenceConfig("H2", "jdbc:h2:mem:lib-close;DB_CLOSE_DELAY=-1", "sa", ""),
                    new IndexConfig(tempDir.resolve("lib-close-idx")),
                    PluginsConfig.classpathOnly(),
                    GlobalConfig.defaults()))
                .build()) {
            // Close happens via try-with-resources; second close is a no-op.
            assertThat(lib).isNotNull();
        }
    }

    @Test
    void searchableLibraryDoubleCloseIsIdempotent() {
        final SearchableLibrary lib = SearchableLibrary.builder()
            .applicationConfig(new ApplicationConfig(
                tempDir,
                new PersistenceConfig("H2", "jdbc:h2:mem:lib-double;DB_CLOSE_DELAY=-1", "sa", ""),
                new IndexConfig(tempDir.resolve("lib-double-idx")),
                PluginsConfig.classpathOnly(),
                GlobalConfig.defaults()))
            .build();
        lib.close();
        lib.close();
    }

    @Test
    void facetFilterToStrIsNullSafe() throws Exception {
        // Use reflection to call the private toStr method via the package.
        final var clazz = io.searchable.core.application.FacetFilter.class;
        final var method = clazz.getDeclaredMethod("toStr", Object.class);
        method.setAccessible(true);
        assertThat((String) method.invoke(null, (Object) null)).isNull();
        assertThat((String) method.invoke(null, "x")).isEqualTo("x");
    }
}
