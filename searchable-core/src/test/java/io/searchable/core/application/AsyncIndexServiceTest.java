package io.searchable.core.application;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncIndexServiceTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private AsyncIndexService async;
    private NamespaceService namespaceService;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("indexes")), AnalyzerFactory.japanese());
        final var nsRepo = new JdbcNamespaceRepository(dataSource);
        final var metaRepo = new JdbcIndexMetadataRepository(dataSource);
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        final LuceneIndexer indexer = new LuceneIndexer(provider, embedding);
        final Clock clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        namespaceService = new NamespaceService(nsRepo, metaRepo, provider,
            GlobalConfig.defaults(), clock);
        namespaceService.create("async_ns", "Async", null);
        async = new AsyncIndexService(
            new IndexService(nsRepo, metaRepo, provider, indexer, clock));
    }

    @AfterEach
    void tearDown() throws Exception {
        async.close();
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void submitProcessesQueuedDocuments() throws Exception {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(async.submit(Document.builder()
                .id("doc-" + i)
                .namespaceId("async_ns")
                .title("title " + i)
                .content("本文 " + i)
                .build()));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get();

        final var stats = async.stats().get("async_ns");
        assertThat(stats.processed()).isEqualTo(5);
        assertThat(stats.failed()).isZero();
        assertThat(stats.queued()).isZero();
    }

    @Test
    void closeRejectsNewSubmissions() {
        async.close();
        assertThatThrownBy(() -> async.submit(Document.builder()
            .id("x").namespaceId("async_ns").title("t").content("c").build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failureIncrementsFailedCounter() {
        // Submitting a document to a non-existent namespace fails inside the worker.
        final CompletableFuture<Void> f = async.submit(Document.builder()
            .id("doc-x")
            .namespaceId("missing_ns")
            .title("t").content("c").build());
        assertThatThrownBy(f::join).isInstanceOf(Exception.class);
        assertThat(async.stats().get("missing_ns").failed()).isEqualTo(1L);
    }
}
