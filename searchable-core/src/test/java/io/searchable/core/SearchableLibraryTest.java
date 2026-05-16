package io.searchable.core;

import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.application.config.IndexConfig;
import io.searchable.core.application.config.PluginsConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.testing.H2TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchableLibraryTest {

    @TempDir
    Path tempDir;

    private H2TestDatabase database;

    @BeforeEach
    void setUp() {
        database = H2TestDatabase.open();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void buildsLibraryFromApplicationConfigUsingProvidedDataSource() {
        try (SearchableLibrary library = SearchableLibrary.builder()
                .applicationConfig(applicationConfig())
                .dataSource(database.dataSource())
                .initializeSchema(false) // H2TestDatabase already initialized
                .build()) {

            assertThat(library.searchService()).isNotNull();
            assertThat(library.indexService()).isNotNull();
            assertThat(library.namespaceService()).isNotNull();
            assertThat(library.indexStatisticsService()).isNotNull();
            assertThat(library.documentBrowser()).isNotNull();
            assertThat(library.pluginLoader()).isNotNull();
            assertThat(library.embeddingProvider()).isNotNull();
            assertThat(library.namespaceRepository()).isNotNull();
        }
    }

    @Test
    void requiresApplicationConfig() {
        assertThatThrownBy(() -> SearchableLibrary.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("applicationConfig");
    }

    @Test
    void indexAndSearchEndToEnd() {
        try (SearchableLibrary library = SearchableLibrary.builder()
                .applicationConfig(applicationConfig())
                .dataSource(database.dataSource())
                .initializeSchema(false)
                .build()) {

            final Namespace ns = library.namespaceService().create(
                "test_ns",
                "Test Namespace",
                NamespaceConfigPatch.empty());
            assertThat(ns.id()).isEqualTo("test_ns");

            library.indexService().index(Document.builder()
                .id("doc-1")
                .namespaceId("test_ns")
                .title("Searchable はライブラリです")
                .content("Searchable は日本語に最適化された全文/ベクトル検索ライブラリです。")
                .indexedAt(Instant.now())
                .build());

            final SearchResult result = library.searchService().search(SearchRequest.builder()
                .query("ライブラリ")
                .namespaceIds(java.util.List.of("test_ns"))
                .build());

            assertThat(result.totalHits()).isPositive();
            assertThat(result.hits()).isNotEmpty();
            assertThat(result.hits().get(0).documentId()).isEqualTo("doc-1");
        }
    }

    @Test
    void readOnlyModeAllowsSearchButRejectsWriteServices() {
        final ApplicationConfig cfg = applicationConfig();
        // First build a writable library and create one namespace + document
        // so the read-only library has something to query.
        try (SearchableLibrary writable = SearchableLibrary.builder()
                .applicationConfig(cfg)
                .dataSource(database.dataSource())
                .initializeSchema(false)
                .build()) {
            writable.namespaceService().create("ro_ns", "RO", NamespaceConfigPatch.empty());
            writable.indexService().index(Document.builder()
                .id("doc-ro")
                .namespaceId("ro_ns")
                .title("read-only doc")
                .content("これは読み込み専用モードのテストです。")
                .indexedAt(Instant.now())
                .build());
        }

        try (SearchableLibrary readOnly = SearchableLibrary.builder()
                .applicationConfig(cfg)
                .dataSource(database.dataSource())
                .initializeSchema(false)
                .readOnly(true)
                .build()) {
            assertThat(readOnly.isReadOnly()).isTrue();
            assertThat(readOnly.searchService()).isNotNull();

            final SearchResult result = readOnly.searchService().search(SearchRequest.builder()
                .query("読み込み")
                .namespaceIds(java.util.List.of("ro_ns"))
                .build());
            assertThat(result.hits()).isNotEmpty();

            assertThatThrownBy(readOnly::indexService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
            assertThatThrownBy(readOnly::namespaceService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
        }
    }

    @Test
    void closeReleasesOwnedResources() {
        final SearchableLibrary library = SearchableLibrary.builder()
            .applicationConfig(applicationConfig())
            .dataSource(database.dataSource())
            .initializeSchema(false)
            .build();
        library.close();
        // Closing twice must remain a no-op.
        library.close();
    }

    private ApplicationConfig applicationConfig() {
        return new ApplicationConfig(
            tempDir,
            new PersistenceConfig("H2", "jdbc:h2:mem:dummy", "sa", ""),
            new IndexConfig(tempDir.resolve("indexes")),
            PluginsConfig.classpathOnly(),
            GlobalConfig.defaults());
    }
}
