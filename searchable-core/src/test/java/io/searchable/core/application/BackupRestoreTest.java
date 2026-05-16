package io.searchable.core.application;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BackupRestoreTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private IndexLayout layout;
    private LuceneIndexProvider provider;
    private LuceneIndexer indexer;
    private NamespaceService namespaceService;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        layout = new IndexLayout(tempDir.resolve("indexes"));
        provider = new LuceneIndexProvider(layout, AnalyzerFactory.japanese());
        final var nsRepo = new JdbcNamespaceRepository(dataSource);
        final var metaRepo = new JdbcIndexMetadataRepository(dataSource);
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        indexer = new LuceneIndexer(provider, embedding);
        namespaceService = new NamespaceService(nsRepo, metaRepo, provider,
            GlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void snapshotAndRestoreRoundTripPreservesIndex() {
        namespaceService.create("bk_ns", "BK", null);
        indexer.index(Document.builder()
            .id("d1").namespaceId("bk_ns")
            .title("Searchable").content("バックアップとリストアの動作確認").build());

        final Path backupRoot = tempDir.resolve("backup");
        final BackupService backups = new BackupService(provider, layout);
        backups.snapshot(backupRoot);
        assertThat(backupRoot.resolve("manifest.txt")).exists();
        assertThat(backupRoot.resolve("indexes").resolve("bk_ns")).isDirectory();

        // Delete the live index and restore from the backup.
        final RestoreService restore = new RestoreService(provider, layout);
        restore.restoreAll(backupRoot);

        final var searcher = new LuceneFullTextSearcher(provider);
        final var result = searcher.search("bk_ns", SearchRequest.builder()
            .query("バックアップ").build());
        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).documentId()).isEqualTo("d1");
    }

    @Test
    void schedulerRunOnceCreatesTimestampedSnapshot() {
        namespaceService.create("sched_ns", "S", null);
        final Path root = tempDir.resolve("scheduled");
        try {
            java.nio.file.Files.createDirectories(root);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        final BackupService backups = new BackupService(provider, layout);
        try (BackupScheduler scheduler = new BackupScheduler(backups, root, 2)) {
            final Path snapshot = scheduler.runOnce();
            assertThat(snapshot).exists();
            assertThat(snapshot.getFileName().toString()).startsWith("snapshot-");
        }
    }

    @Test
    void schedulerRetentionPrunesOldSnapshots() {
        namespaceService.create("retain_ns", "R", null);
        final Path root = tempDir.resolve("retention");
        try {
            java.nio.file.Files.createDirectories(root);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        final BackupService backups = new BackupService(provider, layout);
        try (BackupScheduler scheduler = new BackupScheduler(backups, root, 2)) {
            scheduler.runOnce();
            sleep(Duration.ofMillis(10));
            scheduler.runOnce();
            sleep(Duration.ofMillis(10));
            scheduler.runOnce();
            try (var stream = java.nio.file.Files.list(root)) {
                assertThat(stream.toList()).hasSize(2);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    private static void sleep(final Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
