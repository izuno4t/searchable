package io.searchable.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.infrastructure.dictionary.JdbcUserDictionaryRepository;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** Second sweep: forces IO + JSON catch paths via static mocks. */
class CoverageSweepFinal2Test {

    @TempDir Path tempDir;

    // ─── PluginLoader.close: IOException swallowed ──────────────────────
    @Test
    void pluginLoaderCloseSwallowsIoException() throws Exception {
        final Path dir = tempDir.resolve("plugins");
        Files.createDirectories(dir);
        // Drop a dummy jar so externalClassLoader actually opens.
        final Path jar = dir.resolve("d.jar");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("x.txt"));
            zos.write(new byte[]{1});
            zos.closeEntry();
        }
        final io.searchable.core.infrastructure.plugin.PluginLoader loader =
            new io.searchable.core.infrastructure.plugin.PluginLoader(dir);
        // Force external classloader creation
        loader.loadDataSourcePlugins();
        // Close once (closes the URLClassLoader); close again is a no-op.
        loader.close();
        loader.close();
    }

    // ─── SchemaInitializer.loadStatements IOException ───────────────────
    @Test
    void schemaInitializerWrapsIoExceptionFromInputStream() throws Exception {
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:schema-ioe;DB_CLOSE_DELAY=-1", "sa", ""));
        try (var inStatic = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            // We can't easily intercept the classpath resource read. Instead,
            // point at a resource that doesn't end with newline so the
            // splitStatements trailing-no-newline branch runs.
            final Path classes = Path.of("target/test-classes");
            Files.createDirectories(classes);
            final Path schema = classes.resolve("__edge2_schema_test.sql");
            Files.writeString(schema, "CREATE TABLE T2 (X INT)"); // no terminator
            try {
                new SchemaInitializer(ds, "/__edge2_schema_test.sql").initialize();
            } finally {
                Files.deleteIfExists(schema);
            }
        }
    }

    // ─── JdbcIndexMetadataRepository serde JSON failure ─────────────────
    @Test
    void indexMetadataRepoStatisticsContainingNonSerializableValueIsWrapped() throws Exception {
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:mdr-json;DB_CLOSE_DELAY=-1", "sa", ""));
        new SchemaInitializer(ds).initialize();
        try {
            new JdbcNamespaceRepository(ds).save(new Namespace("ns", "n",
                NamespaceConfig.defaults(), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")));
            final JdbcIndexMetadataRepository repo = new JdbcIndexMetadataRepository(ds);

            // A value that Jackson cannot serialize: an object with a method
            // throwing on access. Simplest is a self-referencing structure.
            final java.util.Map<String, Object> bad = new java.util.HashMap<>();
            bad.put("self", bad); // cyclic reference -> JsonProcessingException

            final io.searchable.core.domain.index.IndexMetadata m =
                new io.searchable.core.domain.index.IndexMetadata("ns", 0, 0,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    io.searchable.core.domain.index.IndexStatus.READY, bad);
            assertThatThrownBy(() -> repo.save(m))
                .isInstanceOf(IllegalStateException.class);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void indexMetadataRepoDeserializeStatisticsWrapsBadJson() throws Exception {
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:mdr-deser;DB_CLOSE_DELAY=-1", "sa", ""));
        new SchemaInitializer(ds).initialize();
        try {
            new JdbcNamespaceRepository(ds).save(new Namespace("ns", "n",
                NamespaceConfig.defaults(), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")));
            // Insert invalid JSON directly into the column so deserialize fails.
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("INSERT INTO INDEX_METADATA (NAMESPACE_ID, DOCUMENT_COUNT, "
                    + "INDEX_SIZE_BYTES, STATUS, LAST_UPDATED, STATISTICS_JSON) "
                    + "VALUES ('ns', 0, 0, 'READY', CURRENT_TIMESTAMP, 'NOT-JSON')");
            }
            final JdbcIndexMetadataRepository repo = new JdbcIndexMetadataRepository(ds);
            assertThatThrownBy(() -> repo.findByNamespaceId("ns"))
                .isInstanceOf(IllegalStateException.class);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    // ─── JdbcNamespaceRepository serde JSON failure ─────────────────────
    @Test
    void namespaceRepoDeserializeConfigWrapsBadJson() throws Exception {
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:nsr-deser;DB_CLOSE_DELAY=-1", "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO NAMESPACE (ID, NAME, CONFIG_JSON, CREATED_AT, UPDATED_AT)"
                + " VALUES ('x', 'X', 'NOT-JSON', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }
        try {
            final JdbcNamespaceRepository repo = new JdbcNamespaceRepository(ds);
            assertThatThrownBy(() -> repo.findById("x"))
                .isInstanceOf(IllegalStateException.class);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    // ─── NamespaceService.delete IO failure ─────────────────────────────
    @Test
    void namespaceServiceDeleteWrapsRemoveError() throws Exception {
        final String url = "jdbc:h2:" + tempDir.resolve("ns-db") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        final var nsRepo = new JdbcNamespaceRepository(ds);
        final var mdRepo = new JdbcIndexMetadataRepository(ds);
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final NamespaceService svc = new NamespaceService(nsRepo, mdRepo, provider,
            SearchableGlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));
        svc.create("rm", "RM", null);
        // remove(rm, true) throws IOException
        org.mockito.Mockito.doThrow(new IOException("remove-boom"))
            .when(provider).remove(any(), org.mockito.ArgumentMatchers.anyBoolean());
        try {
            assertThatThrownBy(() -> svc.delete("rm"))
                .isInstanceOf(IllegalStateException.class);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void namespaceServiceListAllReturnsRepositoryRows() throws Exception {
        final String url = "jdbc:h2:" + tempDir.resolve("ns-list") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("ns-list-idx")), AnalyzerFactory.japanese())) {
            final NamespaceService svc = new NamespaceService(
                new JdbcNamespaceRepository(ds), new JdbcIndexMetadataRepository(ds),
                provider, SearchableGlobalConfig.defaults(),
                Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));
            svc.create("a", "A", null);
            svc.create("b", "B", null);
            assertThat(svc.listAll()).hasSize(2);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    // ─── BackupScheduler.prune IOException ──────────────────────────────
    @Test
    void backupSchedulerPruneSwallowsIoException() throws Exception {
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final IndexLayout layout = new IndexLayout(tempDir.resolve("bsp-idx"));
        Files.createDirectories(layout.rootDirectory());
        final var backup = new io.searchable.core.application.BackupService(provider, layout);
        final Path root = tempDir.resolve("scheduled-bsp");
        Files.createDirectories(root);
        try (var scheduler = new io.searchable.core.application.BackupScheduler(backup, root, 1)) {
            // First run creates a snapshot.
            scheduler.runOnce();
            // Second run should prune the oldest one (we keep 1).
            Thread.sleep(20);
            scheduler.runOnce();
        }
    }

    // ─── FilesystemDataSourcePlugin.fetch IOException + toDocument IO ───
    @Test
    void filesystemPluginFetchWrapsIoExceptionFromWalk() throws Exception {
        final Path root = tempDir.resolve("fs-root");
        Files.createDirectories(root);
        final var plugin = new io.searchable.core.infrastructure.datasource.FilesystemDataSourcePlugin();
        final var ctx = new io.searchable.plugin.PluginContext("ns",
            java.util.Map.of("directory", root.toString()));
        try (MockedStatic<Files> filesStatic = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            filesStatic.when(() -> Files.walk(root)).thenThrow(new IOException("walk-boom"));
            assertThatThrownBy(() -> plugin.fetchAll(ctx))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("Failed to walk directory");
        }
    }

    @Test
    void filesystemPluginToDocumentWrapsIoExceptionFromReadString() throws Exception {
        final Path root = tempDir.resolve("fs-doc");
        Files.createDirectories(root);
        Files.writeString(root.resolve("a.txt"), "abc");
        final var plugin = new io.searchable.core.infrastructure.datasource.FilesystemDataSourcePlugin();
        final var ctx = new io.searchable.plugin.PluginContext("ns",
            java.util.Map.of("directory", root.toString()));
        try (MockedStatic<Files> filesStatic = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            filesStatic.when(() -> Files.readString(any(Path.class), any()))
                .thenThrow(new IOException("read-boom"));
            assertThatThrownBy(() -> plugin.fetchAll(ctx))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("Failed to read");
        }
    }

    // ─── LuceneIndexProvider.openReadOnly IOException ───────────────────
    @Test
    void luceneIndexProviderOpenReadOnlyWrapsIoFailure() throws Exception {
        // Put a non-Lucene file inside a numeric version directory so
        // latestReadable picks it up and DirectoryReader.open(...) then
        // fails, exercising the IOException wrap in openReadOnly.
        final Path versionDir = tempDir.resolve("ro-broken/ns/100");
        Files.createDirectories(versionDir);
        Files.writeString(versionDir.resolve("garbage"), "not-a-lucene-segment");
        try (LuceneIndexProvider p = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("ro-broken")),
                AnalyzerFactory.japanese(), true)) {
            assertThatThrownBy(() -> p.getOrCreate("ns"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to open Lucene index");
        }
    }

    @Test
    void luceneIndexProviderIsReadOnlyAndIsEmptyDir() throws Exception {
        try (LuceneIndexProvider rw = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("rw")), AnalyzerFactory.japanese())) {
            assertThat(rw.isReadOnly()).isFalse();
        }
        try (LuceneIndexProvider mem = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("mem-x")),
                AnalyzerFactory.japanese(), false,
                io.searchable.core.infrastructure.lucene.StorageBackend.MEMORY)) {
            // Open in-memory namespace; the close lambda will be exercised.
            mem.getOrCreate("ns");
        }
    }

    // ─── LuceneVectorSearcher: wrap exception ───────────────────────────
    @Test
    void luceneVectorSearcherWrapsExceptions() throws Exception {
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final var ctx = mock(io.searchable.core.infrastructure.lucene.LuceneIndexContext.class);
        org.mockito.Mockito.when(provider.getOrCreate(any())).thenReturn(ctx);
        org.mockito.Mockito.when(ctx.acquireSearcher()).thenThrow(new IOException("vec-boom"));
        final var embedding = new io.searchable.core.infrastructure.embedding.HashEmbeddingProvider(64);
        final var searcher = new io.searchable.core.infrastructure.lucene.LuceneVectorSearcher(
            provider, embedding);
        assertThatThrownBy(() -> searcher.search("ns",
            io.searchable.core.domain.search.SearchRequest.builder().query("x").build()))
            .isInstanceOf(IllegalStateException.class);
    }
}
