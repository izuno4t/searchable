package io.searchable.core.application;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupRestoreExtraTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private IndexLayout layout;
    private LuceneIndexProvider provider;

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
        final Clock clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        new NamespaceService(nsRepo, metaRepo, provider, GlobalConfig.defaults(), clock)
            .create("bk_ns", "BK", null);
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void backupRejectsNullArguments() {
        final BackupService b = new BackupService(provider, layout);
        assertThatThrownBy(() -> b.snapshot(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.snapshot(tempDir.resolve("out"), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void backupConstructorRejectsNullArgs() {
        assertThatThrownBy(() -> new BackupService(null, layout))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BackupService(provider, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BackupService(provider, layout, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void backupOfMissingNamespaceLogsAndSkipsButCompletes() {
        final BackupService b = new BackupService(provider, layout);
        final var summary = b.snapshot(tempDir.resolve("partial"), List.of("never-created"));
        assertThat(summary.namespaceIds()).containsExactly("never-created");
        assertThat(summary.totalBytes()).isZero();
    }

    @Test
    void backupOpenNamespacesEnumerationReturnsEmptyWhenRootMissing() {
        // Use a fresh layout pointing at a non-existent root.
        final IndexLayout missing = new IndexLayout(tempDir.resolve("never-existed"));
        final BackupService b = new BackupService(provider, missing);
        final var summary = b.snapshot(tempDir.resolve("snapshot-x"));
        assertThat(summary.namespaceIds()).isEmpty();
    }

    @Test
    void restoreServiceRejectsNullArgs() {
        assertThatThrownBy(() -> new RestoreService(null, layout))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RestoreService(provider, null))
            .isInstanceOf(NullPointerException.class);

        final RestoreService r = new RestoreService(provider, layout);
        assertThatThrownBy(() -> r.restoreAll(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> r.restoreOne(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> r.restoreOne(tempDir, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void restoreAllFailsWhenBackupHasNoIndexesDirectory() {
        final RestoreService r = new RestoreService(provider, layout);
        assertThatThrownBy(() -> r.restoreAll(tempDir.resolve("does-not-exist")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("indexes");
    }

    @Test
    void restoreOneFailsWhenNamespaceNotInBackup() throws Exception {
        final Path backup = tempDir.resolve("backup-empty");
        Files.createDirectories(backup.resolve("indexes"));
        final RestoreService r = new RestoreService(provider, layout);
        assertThatThrownBy(() -> r.restoreOne(backup, "missing"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void backupSchedulerRejectsInvalidArgs() {
        final BackupService b = new BackupService(provider, layout);
        assertThatThrownBy(() -> new BackupScheduler(null, tempDir, 1))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BackupScheduler(b, null, 1))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BackupScheduler(b, tempDir, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BackupScheduler(b, tempDir, -3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backupSchedulerStartRejectsBadInterval() {
        final BackupService b = new BackupService(provider, layout);
        try (BackupScheduler s = new BackupScheduler(b, tempDir.resolve("s"), 1)) {
            assertThatThrownBy(() -> s.start(null))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> s.start(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> s.start(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void backupSchedulerStartActuallyRunsBackupAfterInterval() throws Exception {
        final Path scheduledRoot = tempDir.resolve("scheduled");
        Files.createDirectories(scheduledRoot);
        final BackupService b = new BackupService(provider, layout);
        try (BackupScheduler s = new BackupScheduler(b, scheduledRoot, 5)) {
            s.start(Duration.ofMillis(100));
            // Wait long enough for at least one tick.
            Thread.sleep(400);
            try (var stream = Files.list(scheduledRoot)) {
                assertThat(stream.anyMatch(p -> p.getFileName().toString().startsWith("snapshot-")))
                    .isTrue();
            }
        }
    }
}
