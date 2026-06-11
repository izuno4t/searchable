package io.searchable.core.infrastructure.persistence.jdbc;

import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.testing.H2TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcIndexMetadataRepositoryExtraTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private H2TestDatabase db;
    private JdbcNamespaceRepository namespaces;
    private JdbcIndexMetadataRepository metadata;

    @BeforeEach
    void setUp() {
        db = H2TestDatabase.open();
        namespaces = new JdbcNamespaceRepository(db.dataSource());
        metadata = new JdbcIndexMetadataRepository(db.dataSource());
        namespaces.save(new Namespace("ns-a", "a", NamespaceConfig.defaults(), T0, T0));
        namespaces.save(new Namespace("ns-b", "b", NamespaceConfig.defaults(), T0, T0));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new JdbcIndexMetadataRepository(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void apiRejectsNullArgs() {
        assertThatThrownBy(() -> metadata.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metadata.findByNamespaceId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metadata.delete(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void findAllReturnsAllRowsOrderedById() {
        metadata.save(new IndexMetadata("ns-b", 2L, 200L, T0, IndexStatus.READY, Map.of()));
        metadata.save(new IndexMetadata("ns-a", 1L, 100L, T0, IndexStatus.INDEXING, Map.of("k", "v")));

        final List<IndexMetadata> all = metadata.findAll();
        assertThat(all).extracting(IndexMetadata::namespaceId).containsExactly("ns-a", "ns-b");
        assertThat(all.get(0).statistics()).containsEntry("k", "v");
    }

    @Test
    void emptyStatisticsSerializedAsNullJsonAndRoundTrips() {
        metadata.save(new IndexMetadata("ns-a", 0L, 0L, T0, IndexStatus.EMPTY, Map.of()));
        final IndexMetadata loaded = metadata.findByNamespaceId("ns-a").orElseThrow();
        assertThat(loaded.statistics()).isEmpty();
    }

    @Test
    void deserializeStatisticsHandlesBlankJsonColumn() throws Exception {
        // STATISTICS_JSON explicitly set to a blank string forces the
        // `json.isBlank()` branch of deserializeStatistics.
        try (var c = db.dataSource().getConnection();
             var ps = c.prepareStatement(
                 "MERGE INTO INDEX_METADATA (NAMESPACE_ID, DOCUMENT_COUNT, "
                 + "INDEX_SIZE_BYTES, STATUS, LAST_UPDATED, STATISTICS_JSON) "
                 + "KEY (NAMESPACE_ID) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, "ns-a");
            ps.setLong(2, 0);
            ps.setLong(3, 0);
            ps.setString(4, IndexStatus.EMPTY.name());
            ps.setTimestamp(5, java.sql.Timestamp.from(T0));
            ps.setString(6, "   ");
            ps.executeUpdate();
        }
        final IndexMetadata loaded = metadata.findByNamespaceId("ns-a").orElseThrow();
        assertThat(loaded.statistics()).isEmpty();
    }

    @Test
    void sqlErrorsWrappedAsIllegalState() throws Exception {
        try (var c = db.dataSource().getConnection(); var s = c.createStatement()) {
            s.execute("DROP TABLE INDEX_METADATA");
        }
        final IndexMetadata m = IndexMetadata.empty("ns-a", T0);
        assertThatThrownBy(() -> metadata.save(m)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> metadata.findByNamespaceId("ns-a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> metadata.findAll()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> metadata.delete("ns-a")).isInstanceOf(IllegalStateException.class);
    }
}
