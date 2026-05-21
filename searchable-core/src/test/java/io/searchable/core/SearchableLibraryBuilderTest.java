package io.searchable.core;

import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.application.config.GlobalConfigProvider;
import io.searchable.core.application.config.IndexConfig;
import io.searchable.core.application.config.PluginsConfig;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import io.searchable.core.infrastructure.dictionary.JdbcUserDictionaryRepository;
import io.searchable.core.infrastructure.plugin.PluginLoader;
import io.searchable.core.testing.H2TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SearchableLibraryBuilderTest {

    @TempDir Path tempDir;
    private H2TestDatabase db;

    @BeforeEach
    void setUp() { db = H2TestDatabase.open(); }

    @AfterEach
    void tearDown() { db.close(); }

    private ApplicationConfig appConfig() {
        return new ApplicationConfig(
            tempDir,
            new PersistenceConfig("H2", "jdbc:h2:mem:dummy", "sa", ""),
            new IndexConfig(tempDir.resolve("idx")),
            PluginsConfig.classpathOnly(),
            GlobalConfig.defaults());
    }

    @Test
    void allBuilderSettersAreReachableAndGettersExposed() {
        final var nsRepo = new JdbcNamespaceRepository(db.dataSource());
        final var metaRepo = new JdbcIndexMetadataRepository(db.dataSource());
        final var dictRepo = new JdbcUserDictionaryRepository(db.dataSource());
        final var docMetaRepo = new JdbcDocumentMetadataRepository(db.dataSource());
        final var globalCfg = GlobalConfig.defaults();
        final var globalProvider = new GlobalConfigProvider(globalCfg);
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        final AnalyzerFactory analyzer = AnalyzerFactory.japanese();
        final PluginLoader plugins = new PluginLoader();
        final Clock clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);

        try (SearchableLibrary lib = SearchableLibrary.builder()
                .applicationConfig(appConfig())
                .dataSource(db.dataSource())
                .initializeSchema(false)
                .namespaceRepository(nsRepo)
                .indexMetadataRepository(metaRepo)
                .dictionaryRepository(dictRepo)
                .documentMetadataRepository(docMetaRepo)
                .globalConfig(globalCfg)
                .globalConfigProvider(globalProvider)
                .embeddingProvider(embedding)
                .analyzerFactory(analyzer)
                .pluginLoader(plugins)
                .clock(clock)
                .build()) {

            // Verify every public getter on the assembled library reads cleanly.
            assertThat(lib.configuration()).isNotNull();
            assertThat(lib.namespaceRepository()).isSameAs(nsRepo);
            assertThat(lib.indexMetadataRepository()).isSameAs(metaRepo);
            assertThat(lib.dictionaryRepository()).isSameAs(dictRepo);
            assertThat(lib.documentMetadataRepository()).isSameAs(docMetaRepo);
            assertThat(lib.globalConfigProvider()).isNotNull();
            assertThat(lib.indexProvider()).isNotNull();
            assertThat(lib.indexer()).isNotNull();
            assertThat(lib.hybridSearchOrchestrator()).isNotNull();
            assertThat(lib.embeddingProvider()).isSameAs(embedding);
        }
    }

    @Test
    void fromConfigBuildsWithDefaults() {
        try (SearchableLibrary lib = SearchableLibrary.fromConfig(appConfig())) {
            assertThat(lib).isNotNull();
            assertThat(lib.searchService()).isNotNull();
        }
    }

    @Test
    void closeIsIdempotent() {
        final SearchableLibrary lib = SearchableLibrary.builder()
            .applicationConfig(appConfig())
            .dataSource(db.dataSource())
            .initializeSchema(false)
            .build();
        lib.close();
        lib.close(); // second close is a no-op
    }
}
