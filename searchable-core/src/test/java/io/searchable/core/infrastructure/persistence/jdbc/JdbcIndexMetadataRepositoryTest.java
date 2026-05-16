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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcIndexMetadataRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    private H2TestDatabase db;
    private JdbcNamespaceRepository namespaces;
    private JdbcIndexMetadataRepository metadata;

    @BeforeEach
    void setUp() {
        db = H2TestDatabase.open();
        namespaces = new JdbcNamespaceRepository(db.dataSource());
        metadata = new JdbcIndexMetadataRepository(db.dataSource());
        namespaces.save(new Namespace("ns-1", "n", NamespaceConfig.defaults(), T0, T0));
    }

    @AfterEach
    void tearDown() {
        db.close();
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
