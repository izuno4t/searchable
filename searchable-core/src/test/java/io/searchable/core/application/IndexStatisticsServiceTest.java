package io.searchable.core.application;

import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexMetadataRepository;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.domain.namespace.NamespaceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IndexStatisticsServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void emptyRepositoryYieldsZeroes() {
        final IndexStatisticsService svc = new IndexStatisticsService(
            new InMemoryNamespaceRepo(List.of()),
            new InMemoryMetadataRepo(Map.of()));

        final IndexStatisticsService.Statistics stats = svc.aggregate();

        assertThat(stats.namespaceCount()).isZero();
        assertThat(stats.documentCount()).isZero();
        assertThat(stats.indexSizeBytes()).isZero();
        assertThat(stats.lastUpdated()).isNull();
    }

    @Test
    void aggregatesDocumentAndByteCountsAcrossNamespaces() {
        final List<Namespace> namespaces = List.of(ns("a"), ns("b"), ns("c"));
        final Map<String, IndexMetadata> metadata = Map.of(
            "a", new IndexMetadata("a", 10L, 1_000L, T0, IndexStatus.READY, Map.of()),
            "b", new IndexMetadata("b", 25L, 4_000L, T2, IndexStatus.READY, Map.of()),
            "c", new IndexMetadata("c",  5L,   500L, T1, IndexStatus.READY, Map.of()));

        final IndexStatisticsService.Statistics stats = new IndexStatisticsService(
            new InMemoryNamespaceRepo(namespaces),
            new InMemoryMetadataRepo(metadata)).aggregate();

        assertThat(stats.namespaceCount()).isEqualTo(3);
        assertThat(stats.documentCount()).isEqualTo(40L);
        assertThat(stats.indexSizeBytes()).isEqualTo(5_500L);
        assertThat(stats.lastUpdated()).isEqualTo(T2);
    }

    @Test
    void skipsNamespacesWithoutMetadataButCountsThemInTotal() {
        final List<Namespace> namespaces = List.of(ns("with-meta"), ns("no-meta"));
        final Map<String, IndexMetadata> metadata = Map.of(
            "with-meta", new IndexMetadata("with-meta", 3L, 100L, T0, IndexStatus.READY, Map.of()));

        final IndexStatisticsService.Statistics stats = new IndexStatisticsService(
            new InMemoryNamespaceRepo(namespaces),
            new InMemoryMetadataRepo(metadata)).aggregate();

        assertThat(stats.namespaceCount()).isEqualTo(2);
        assertThat(stats.documentCount()).isEqualTo(3L);
        assertThat(stats.indexSizeBytes()).isEqualTo(100L);
        assertThat(stats.lastUpdated()).isEqualTo(T0);
    }

    @Test
    void earlierTimestampDoesNotOverrideLater() {
        final List<Namespace> namespaces = List.of(ns("first"), ns("second"));
        final Map<String, IndexMetadata> metadata = Map.of(
            "first",  new IndexMetadata("first",  1L, 1L, T2, IndexStatus.READY, Map.of()),
            "second", new IndexMetadata("second", 1L, 1L, T0, IndexStatus.READY, Map.of()));

        // The iteration order depends on findAll(); both namespaces feed in
        // and the most recent timestamp must win.
        final IndexStatisticsService.Statistics stats = new IndexStatisticsService(
            new InMemoryNamespaceRepo(namespaces),
            new InMemoryMetadataRepo(metadata)).aggregate();

        assertThat(stats.lastUpdated()).isEqualTo(T2);
    }

    private static Namespace ns(final String id) {
        return new Namespace(id, id, NamespaceConfig.defaults(), T0, T0);
    }

    private record InMemoryNamespaceRepo(List<Namespace> all) implements NamespaceRepository {
        @Override public void save(final Namespace namespace) { throw new UnsupportedOperationException(); }
        @Override public Optional<Namespace> findById(final String id) {
            return all.stream().filter(n -> n.id().equals(id)).findFirst();
        }
        @Override public List<Namespace> findAll() { return new ArrayList<>(all); }
        @Override public boolean delete(final String id) { return false; }
        @Override public boolean exists(final String id) { return findById(id).isPresent(); }
    }

    private record InMemoryMetadataRepo(Map<String, IndexMetadata> store) implements IndexMetadataRepository {
        @Override public void save(final IndexMetadata metadata) { throw new UnsupportedOperationException(); }
        @Override public Optional<IndexMetadata> findByNamespaceId(final String namespaceId) {
            return Optional.ofNullable(store.get(namespaceId));
        }
        @Override public List<IndexMetadata> findAll() {
            return store.values().stream().collect(Collectors.toUnmodifiableList());
        }
        @Override public boolean delete(final String namespaceId) { return false; }
    }
}
