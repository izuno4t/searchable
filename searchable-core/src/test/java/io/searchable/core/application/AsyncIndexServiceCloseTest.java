package io.searchable.core.application;

import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.domain.document.Document;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;

class AsyncIndexServiceCloseTest {

    @TempDir Path tempDir;

    @Test
    void closeForcesShutdownWhenTimeoutExpires() throws Exception {
        final String url = "jdbc:h2:" + tempDir.resolve("ai-close") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("ai-idx")), AnalyzerFactory.japanese())) {
            final var nsRepo = new JdbcNamespaceRepository(ds);
            final var mdRepo = new JdbcIndexMetadataRepository(ds);
            final Clock clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
            new NamespaceService(nsRepo, mdRepo, provider, SearchableGlobalConfig.defaults(), clock)
                .create("ns", "N", null);
            final IndexService svc = new IndexService(nsRepo, mdRepo, provider,
                new LuceneIndexer(provider, new HashEmbeddingProvider(64)), clock);

            // Use a mocked IndexService that blocks the worker thread so
            // awaitTermination times out and the shutdownNow branch runs.
            final CountDownLatch latch = new CountDownLatch(1);
            final IndexService blocking = org.mockito.Mockito.mock(IndexService.class);
            org.mockito.Mockito.doAnswer(inv -> {
                latch.await();
                return null;
            }).when(blocking).index(org.mockito.ArgumentMatchers.any(Document.class));

            final var async = new AsyncIndexService(blocking, Duration.ofMillis(1));
            async.submit(Document.builder().id("d").namespaceId("ns").title("t")
                .content("c").build());
            async.close();    // timeout fires -> shutdownNow() branch
            latch.countDown(); // release the blocked task
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }
}
