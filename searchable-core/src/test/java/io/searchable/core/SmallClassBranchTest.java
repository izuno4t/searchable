package io.searchable.core;

import io.searchable.core.application.AnchorUrls;
import io.searchable.core.application.FacetAggregator;
import io.searchable.core.application.config.SearchableConfig;
import io.searchable.core.domain.search.FacetSpec;
import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.infrastructure.lucene.LuceneDocumentMapper;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.plugin.PluginLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Branch supplement for assorted small-line-count classes inside
 * {@code searchable-core}. Each test documents the specific uncovered
 * branch it targets.
 */
class SmallClassBranchTest {

    @TempDir Path tempDir;

    @Test
    void anchorUrlsTrimsTrailingDashFromSlug() {
        assertThat(AnchorUrls.slugify("abc!")).isEqualTo("abc");
    }

    @Test
    void anchorUrlsHandlesNullBaseAndPresentHeading() {
        assertThat(AnchorUrls.anchorFor(null, "Heading")).isEqualTo("#heading");
    }

    // FacetSpec.content rejects a blank tag key at construction, so the
    // blank-key branch inside FacetAggregator is unreachable from valid
    // spec instances.

    @Test
    void searchableConfigNormalizeH2UrlHandlesAbsolutePathUnchanged() {
        // Absolute H2 URL passes through with normaliser-added flags
        // (AUTO_SERVER=TRUE etc.) but the original prefix is preserved.
        final String url = "jdbc:h2:" + tempDir.resolve("db");
        assertThat(SearchableConfig.normalizeH2Url(url, tempDir)).startsWith(url);
    }

    @Test
    void searchableConfigNormalizeH2UrlResolvesRelativePath() {
        final String relative = "jdbc:h2:./mydb";
        assertThat(SearchableConfig.normalizeH2Url(relative, tempDir))
            .contains(tempDir.toString());
    }

    @Test
    void searchableConfigNormalizeH2UrlPassesThroughNonH2Url() {
        final String pg = "jdbc:postgresql://host/db";
        assertThat(SearchableConfig.normalizeH2Url(pg, tempDir)).isEqualTo(pg);
    }

    @Test
    void searchableConfigNormalizeH2UrlPassesThroughNullAndBlank() {
        assertThat(SearchableConfig.normalizeH2Url(null, tempDir)).isNull();
        assertThat(SearchableConfig.normalizeH2Url("", tempDir)).isEmpty();
    }

    @Test
    void dataSourceFactoryRejectsUnknownPersistenceType() {
        final PersistenceConfig cfg = new PersistenceConfig(
            "Cassandra", "jdbc:cassandra://x", "u", "p");
        assertThatThrownBy(() -> DataSourceFactory.create(cfg))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported persistence type");
    }

    @Test
    void dataSourceFactoryAcceptsJdbcType() throws Exception {
        final String url = "jdbc:h2:mem:dsfactory-jdbc-test;DB_CLOSE_DELAY=-1";
        final PersistenceConfig cfg = new PersistenceConfig("JDBC", url, "sa", "");
        final DataSource ds = DataSourceFactory.create(cfg);
        try (var conn = ds.getConnection()) {
            assertThat(conn.isValid(1)).isTrue();
        }
        if (ds instanceof AutoCloseable c) c.close();
    }

    @Test
    void schemaInitializerSecondCallIsIdempotent() throws Exception {
        final String url = "jdbc:h2:" + tempDir.resolve("schema-test")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(
            new PersistenceConfig("H2", url, "sa", ""));
        try {
            new SchemaInitializer(ds).initialize();
            new SchemaInitializer(ds).initialize();
        } finally {
            if (ds instanceof AutoCloseable c) c.close();
        }
    }

    @Test
    void pluginLoaderReturnsOverviewWhenDirectoryIsEmpty() throws Exception {
        Files.createDirectories(tempDir.resolve("plugins-empty"));
        try (var loader = new PluginLoader(tempDir.resolve("plugins-empty"))) {
            assertThat(loader.overview()).isNotNull();
        }
    }

    @Test
    void pluginLoaderHandlesAbsentDirectory() throws Exception {
        try (var loader = new PluginLoader(tempDir.resolve("never-existed"))) {
            assertThat(loader.overview()).isNotNull();
        }
    }

    @Test
    void pluginLoaderHandlesNullDirectory() throws Exception {
        try (var loader = new PluginLoader(null)) {
            assertThat(loader.overview()).isNotNull();
        }
    }

    @Test
    void luceneDocumentMapperSerializesNullAndEmptyMetadataAsEmptyObject() throws Exception {
        final var mapper = new LuceneDocumentMapper();
        final var m = LuceneDocumentMapper.class.getDeclaredMethod(
            "serializeMetadata", java.util.Map.class);
        m.setAccessible(true);
        assertThat((String) m.invoke(mapper, (java.util.Map<String, Object>) null))
            .isEqualTo("{}");
        assertThat((String) m.invoke(mapper, java.util.Map.of())).isEqualTo("{}");
        final Map<String, Object> populated = new HashMap<>();
        populated.put("k", "v");
        assertThat((String) m.invoke(mapper, populated)).contains("\"k\"");
    }
}
