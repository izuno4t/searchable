package io.searchable.core;

import io.searchable.core.application.IndexService;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.SearchPerformanceMonitor;
import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentSourceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

class FinalCoverageMopUpTest {

    @TempDir Path tempDir;

    @Test
    void searchPerformanceMonitorNoArgConstructor() {
        final var m = new SearchPerformanceMonitor();
        m.record(5L);
        assertThat(m.summary().count()).isEqualTo(1L);
    }

    @Test
    void searchPerformanceMonitorIgnoresNegativeLatency() {
        final var m = new SearchPerformanceMonitor();
        m.record(-1L); // hits the latencyMs<0 return branch
        assertThat(m.summary().count()).isZero();
    }

    @Test
    void hashEmbeddingProviderRejectsNonPositiveDimension() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new HashEmbeddingProvider(0))
            .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new HashEmbeddingProvider(7))   // not multiple of 8
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexServiceIndexIfChangedDelegatesToIndexWhenSourcesNull() throws Exception {
        // documentSources == null path inside indexIfChanged short-circuits
        // to plain index(); ensure the call returns true and records nothing.
        final String url = "jdbc:h2:" + tempDir.resolve("ifc-null") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("ifc")), AnalyzerFactory.japanese())) {
            final var nsRepo = new JdbcNamespaceRepository(ds);
            final var mdRepo = new JdbcIndexMetadataRepository(ds);
            final Clock clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
            new NamespaceService(nsRepo, mdRepo, provider, GlobalConfig.defaults(), clock)
                .create("ic", "I", null);
            final IndexService svc = new IndexService(nsRepo, mdRepo, provider,
                new LuceneIndexer(provider, new HashEmbeddingProvider(64)), clock);
            assertThat(svc.indexIfChanged(Document.builder()
                .id("d").namespaceId("ic").title("t").content("c").build())).isTrue();
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }
}
