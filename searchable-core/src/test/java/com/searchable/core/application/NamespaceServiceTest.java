package com.searchable.core.application;

import com.searchable.core.application.config.GlobalConfig;
import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.namespace.NamespaceConfigPatch;
import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;
import com.searchable.core.infrastructure.lucene.AnalyzerFactory;
import com.searchable.core.infrastructure.lucene.IndexLayout;
import com.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import com.searchable.core.infrastructure.persistence.DataSourceFactory;
import com.searchable.core.infrastructure.persistence.PersistenceConfig;
import com.searchable.core.infrastructure.persistence.SchemaInitializer;
import com.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import com.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceServiceTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider indexProvider;
    private NamespaceService service;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        indexProvider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("indexes")),
            AnalyzerFactory.japanese());
        service = new NamespaceService(
            new JdbcNamespaceRepository(dataSource),
            new JdbcIndexMetadataRepository(dataSource),
            indexProvider,
            GlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        indexProvider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void createPersistsAndOpensIndex() {
        final Namespace ns = service.create("ns-1", "Project A", null);

        assertThat(ns.id()).isEqualTo("ns-1");
        assertThat(ns.config().architecture()).isEqualTo(SearchType.FULL_TEXT);
        assertThat(service.findById("ns-1")).isPresent();
        assertThat(Files.isDirectory(tempDir.resolve("indexes/ns-1"))).isTrue();
    }

    @Test
    void createMergesGlobalDefaultsForOmittedFields() {
        final NamespaceConfigPatch partial = new NamespaceConfigPatch(
            SearchType.HYBRID, null, null, null, null, Map.of());

        final Namespace ns = service.create("ns-1", "n", partial);

        assertThat(ns.config().architecture()).isEqualTo(SearchType.HYBRID);
        assertThat(ns.config().searchStrategy()).isEqualTo(SearchStrategy.SEQUENTIAL);
        assertThat(ns.config().searchOrder()).isEqualTo(SearchOrder.FULL_TEXT_FIRST);
    }

    @Test
    void createRejectsDuplicateId() {
        service.create("ns-1", "n", null);
        assertThatThrownBy(() -> service.create("ns-1", "n", null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateConfigPersistsChanges() {
        service.create("ns-1", "n", null);
        final NamespaceConfigPatch patch = new NamespaceConfigPatch(
            SearchType.VECTOR, SearchStrategy.PARALLEL,
            SearchOrder.VECTOR_FIRST, null, null, null);

        final Namespace updated = service.updateConfig("ns-1", patch);
        assertThat(updated.config().architecture()).isEqualTo(SearchType.VECTOR);
        assertThat(service.findById("ns-1").orElseThrow().config().architecture())
            .isEqualTo(SearchType.VECTOR);
    }

    @Test
    void renameUpdatesName() {
        service.create("ns-1", "old", null);
        service.rename("ns-1", "new");
        assertThat(service.findById("ns-1").orElseThrow().name()).isEqualTo("new");
    }

    @Test
    void deleteRemovesPersistenceAndIndexFiles() {
        service.create("ns-1", "n", null);
        assertThat(service.delete("ns-1")).isTrue();
        assertThat(service.findById("ns-1")).isEmpty();
        assertThat(Files.exists(tempDir.resolve("indexes/ns-1"))).isFalse();
        assertThat(service.delete("ns-1")).isFalse();
    }
}
