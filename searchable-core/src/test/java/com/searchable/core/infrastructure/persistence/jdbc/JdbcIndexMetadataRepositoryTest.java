package com.searchable.core.infrastructure.persistence.jdbc;

import com.searchable.core.domain.index.IndexMetadata;
import com.searchable.core.domain.index.IndexStatus;
import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.namespace.NamespaceConfig;
import com.searchable.core.infrastructure.persistence.DataSourceFactory;
import com.searchable.core.infrastructure.persistence.PersistenceConfig;
import com.searchable.core.infrastructure.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcIndexMetadataRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    @TempDir Path tempDir;

    private DataSource dataSource;
    private JdbcNamespaceRepository namespaces;
    private JdbcIndexMetadataRepository metadata;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("test") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        namespaces = new JdbcNamespaceRepository(dataSource);
        metadata = new JdbcIndexMetadataRepository(dataSource);
        namespaces.save(new Namespace("ns-1", "n", NamespaceConfig.defaults(), T0, T0));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void saveAndFindRoundTripsAllFields() {
        final IndexMetadata m = new IndexMetadata("ns-1", 100L, 1_000_000L,
            T1, IndexStatus.READY, Map.of("segments", 4, "deletes", 0));
        metadata.save(m);

        final IndexMetadata loaded = metadata.findByNamespaceId("ns-1").orElseThrow();
        assertThat(loaded.documentCount()).isEqualTo(100L);
        assertThat(loaded.indexSizeBytes()).isEqualTo(1_000_000L);
        assertThat(loaded.status()).isEqualTo(IndexStatus.READY);
        assertThat(loaded.lastUpdated()).isEqualTo(T1);
        assertThat(loaded.statistics()).containsEntry("segments", 4).containsEntry("deletes", 0);
    }

    @Test
    void saveActsAsUpsert() {
        metadata.save(IndexMetadata.empty("ns-1", T0));
        metadata.save(new IndexMetadata("ns-1", 5L, 100L,
            T1, IndexStatus.INDEXING, Map.of()));

        final IndexMetadata loaded = metadata.findByNamespaceId("ns-1").orElseThrow();
        assertThat(loaded.documentCount()).isEqualTo(5L);
        assertThat(loaded.status()).isEqualTo(IndexStatus.INDEXING);
    }

    @Test
    void findReturnsEmptyForUnknown() {
        assertThat(metadata.findByNamespaceId("missing")).isEmpty();
    }

    @Test
    void deleteRemovesRow() {
        metadata.save(IndexMetadata.empty("ns-1", T0));
        assertThat(metadata.delete("ns-1")).isTrue();
        assertThat(metadata.findByNamespaceId("ns-1")).isEmpty();
        assertThat(metadata.delete("ns-1")).isFalse();
    }

    @Test
    void deletingNamespaceCascadesToIndexMetadata() {
        metadata.save(IndexMetadata.empty("ns-1", T0));
        namespaces.delete("ns-1");
        assertThat(metadata.findByNamespaceId("ns-1")).isEmpty();
    }
}
