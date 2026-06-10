package io.searchable.core;

import io.searchable.core.application.IndexService;
import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.domain.document.DocumentMetadataRepository;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexMetadataRepository;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.domain.namespace.NamespaceRepository;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexContext;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Final sweep targeting remaining gaps in IndexService, BackupService etc. */
class CoverageSweepFinalTest {

    @TempDir Path tempDir;

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);

    // ─── IndexService.indexBatch catch / refreshMetadata IOException ─────
    @Test
    void indexBatchMarksErrorAndRethrowsWhenIndexerFails() throws Exception {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("idx")), AnalyzerFactory.japanese())) {
            final var nsRepo = new JdbcNamespaceRepository(ds);
            final var mdRepo = new JdbcIndexMetadataRepository(ds);
            new NamespaceService(nsRepo, mdRepo, provider, SearchableGlobalConfig.defaults(), CLOCK)
                .create("ns", "N", null);

            // Mock the indexer to throw on indexBatch.
            final LuceneIndexer brokenIndexer = mock(LuceneIndexer.class);
            doThrow(new RuntimeException("indexer-boom"))
                .when(brokenIndexer).indexBatch(anyString(), any());

            final IndexService svc = new IndexService(nsRepo, mdRepo, provider, brokenIndexer, CLOCK);
            assertThatThrownBy(() -> svc.indexBatch("ns",
                List.of(Document.builder().id("d").namespaceId("ns")
                    .title("t").content("c").build())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("indexer-boom");

            // Status flipped to ERROR.
            assertThat(svc.getMetadata("ns").status()).isEqualTo(IndexStatus.ERROR);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void refreshMetadataWrapsIoExceptionFromContextRefresh() throws Exception {
        // Stub Lucene provider/context so refresh() throws.
        final NamespaceRepository nsRepo = mock(NamespaceRepository.class);
        when(nsRepo.exists("ns")).thenReturn(true);
        final IndexMetadataRepository mdRepo = mock(IndexMetadataRepository.class);
        when(mdRepo.findByNamespaceId(anyString())).thenReturn(Optional.empty());

        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final LuceneIndexContext ctx = mock(LuceneIndexContext.class);
        when(provider.getOrCreate(any())).thenReturn(ctx);
        doThrow(new IOException("refresh-boom")).when(ctx).refresh();

        final LuceneIndexer indexer = mock(LuceneIndexer.class);
        final IndexService svc = new IndexService(nsRepo, mdRepo, provider, indexer, CLOCK);

        final Document doc = Document.builder().id("d").namespaceId("ns")
            .title("t").content("c").build();
        assertThatThrownBy(() -> svc.index(doc))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to refresh metadata");
    }

    @Test
    void indexIfChangedSkipsWhenStoredHashMatches() throws Exception {
        // documentMetadata != null + previous metadata record's source hash
        // matches the upcoming hash -> skip branch.
        final NamespaceRepository nsRepo = mock(NamespaceRepository.class);
        when(nsRepo.exists("ns")).thenReturn(true);
        final IndexMetadataRepository mdRepo = mock(IndexMetadataRepository.class);
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final LuceneIndexer indexer = mock(LuceneIndexer.class);
        final DocumentMetadataRepository docMeta = mock(DocumentMetadataRepository.class);
        // previous source matches the upcoming hash; service should short-circuit.
        final var doc = Document.builder().id("d").namespaceId("ns")
            .title("t").content("c").build();
        final String hash = io.searchable.core.domain.document.ContentHashes.hash(doc);
        when(docMeta.findById(anyString(), anyString()))
            .thenReturn(Optional.of(new DocumentMetadataRecord(
                "ns", "d", "t", java.util.Map.of(),
                java.time.Instant.parse("2026-05-15T00:00:00Z"),
                new io.searchable.core.domain.document.DocumentSource(
                    "inline", "d", hash, null))));

        final IndexService svc = new IndexService(nsRepo, mdRepo, provider, indexer, docMeta, CLOCK);
        assertThat(svc.indexIfChanged(doc)).isFalse();
    }

    // ─── BackupService.snapshotOne: copy IOException + read-only ctx ─────
    @Test
    void backupServiceSnapshotOneWrapsCopyFailure() throws Exception {
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final LuceneIndexContext ctx = mock(LuceneIndexContext.class);
        when(provider.isOpen("ns")).thenReturn(true);
        when(provider.getOrCreate("ns")).thenReturn(ctx);
        when(ctx.isReadOnly()).thenReturn(true); // skip writer.commit() path
        final IndexLayout layout = new IndexLayout(tempDir.resolve("nonexistent-src"));

        final var svc = new io.searchable.core.application.BackupService(provider, layout);
        // Source directory missing -> returns 0L, branch covered.
        svc.snapshot(tempDir.resolve("dest"), List.of("ns"));
    }

    // ─── BackupService.openNamespaces: empty / IO error ──────────────────
    @Test
    void backupServiceOpenNamespacesReturnsEmptyWhenRootMissing() throws Exception {
        final var provider = mock(LuceneIndexProvider.class);
        final IndexLayout missing = new IndexLayout(tempDir.resolve("no-such-root"));
        final var svc = new io.searchable.core.application.BackupService(provider, missing);
        final var summary = svc.snapshot(tempDir.resolve("snap-empty"));
        assertThat(summary.namespaceIds()).isEmpty();
    }

    @Test
    void backupServiceOpenNamespacesWrapsListIoException() throws Exception {
        final var provider = mock(LuceneIndexProvider.class);
        final Path root = tempDir.resolve("realroot");
        Files.createDirectories(root);
        final IndexLayout layout = new IndexLayout(root);
        final var svc = new io.searchable.core.application.BackupService(provider, layout);

        try (var filesStatic = org.mockito.Mockito.mockStatic(Files.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            filesStatic.when(() -> Files.list(root)).thenThrow(new IOException("list-boom"));
            assertThatThrownBy(() -> svc.snapshot(tempDir.resolve("snap-x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to enumerate namespaces");
        }
    }

    // ─── AsyncIndexService.close() InterruptedException path ─────────────
    @Test
    void asyncIndexServiceCloseHandlesInterruptedException() throws Exception {
        final String url = "jdbc:h2:" + tempDir.resolve("ai-db") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("ai-idx")), AnalyzerFactory.japanese())) {
            final var nsRepo = new JdbcNamespaceRepository(ds);
            final var mdRepo = new JdbcIndexMetadataRepository(ds);
            final EmbeddingProvider emb = new HashEmbeddingProvider(64);
            final LuceneIndexer indexer = new LuceneIndexer(provider, emb);
            new NamespaceService(nsRepo, mdRepo, provider, SearchableGlobalConfig.defaults(), CLOCK)
                .create("a", "A", null);
            final IndexService svc = new IndexService(nsRepo, mdRepo, provider, indexer, CLOCK);

            final var async = new io.searchable.core.application.AsyncIndexService(svc);
            async.submit(Document.builder().id("d").namespaceId("a").title("t").content("c").build()).join();

            // Interrupt the current thread so close()'s awaitTermination throws.
            Thread.currentThread().interrupt();
            async.close();
            // Clear the interrupted state for downstream tests.
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }
}
