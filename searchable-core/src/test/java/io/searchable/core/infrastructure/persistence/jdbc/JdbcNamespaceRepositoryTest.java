package io.searchable.core.infrastructure.persistence.jdbc;

import io.searchable.core.domain.namespace.AiConfig;
import io.searchable.core.domain.namespace.EmbeddingConfig;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import io.searchable.core.testing.H2TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcNamespaceRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    private H2TestDatabase db;
    private JdbcNamespaceRepository repository;

    @BeforeEach
    void setUp() {
        db = H2TestDatabase.open();
        repository = new JdbcNamespaceRepository(db.dataSource());
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void saveAndFindRoundTripsAllFields() {
        final NamespaceConfig config = new NamespaceConfig(
            SearchType.HYBRID,
            SearchStrategy.PARALLEL,
            SearchOrder.VECTOR_FIRST,
            new EmbeddingConfig("multilingual-e5-small", 384),
            AiConfig.disabled(),
            Map.of("custom-key", "custom-value")
        );
        repository.save(new Namespace("ns-1", "プロジェクトA", config, T0, T1));

        final Optional<Namespace> loaded = repository.findById("ns-1");
        assertThat(loaded).isPresent();
        final Namespace ns = loaded.get();
        assertThat(ns.id()).isEqualTo("ns-1");
        assertThat(ns.name()).isEqualTo("プロジェクトA");
        assertThat(ns.createdAt()).isEqualTo(T0);
        assertThat(ns.updatedAt()).isEqualTo(T1);
        assertThat(ns.config().architecture()).isEqualTo(SearchType.HYBRID);
        assertThat(ns.config().searchStrategy()).isEqualTo(SearchStrategy.PARALLEL);
        assertThat(ns.config().embeddingConfig()).isNotNull();
        assertThat(ns.config().embeddingConfig().model()).isEqualTo("multilingual-e5-small");
        assertThat(ns.config().customParams()).containsEntry("custom-key", "custom-value");
    }

    @Test
    void findByIdReturnsEmptyForMissing() {
        assertThat(repository.findById("missing")).isEmpty();
    }

    @Test
    void existsReflectsStoredRow() {
        assertThat(repository.exists("ns-1")).isFalse();
        repository.save(new Namespace("ns-1", "n", NamespaceConfig.defaults(), T0, T0));
        assertThat(repository.exists("ns-1")).isTrue();
    }

    @Test
    void saveActsAsUpsert() {
        repository.save(new Namespace("ns-1", "Old", NamespaceConfig.defaults(), T0, T0));
        repository.save(new Namespace("ns-1", "New", NamespaceConfig.defaults(), T0, T1));

        final Namespace loaded = repository.findById("ns-1").orElseThrow();
        assertThat(loaded.name()).isEqualTo("New");
        assertThat(loaded.updatedAt()).isEqualTo(T1);
    }

    @Test
    void findAllReturnsSortedById() {
        repository.save(new Namespace("ns-2", "B", NamespaceConfig.defaults(), T0, T0));
        repository.save(new Namespace("ns-1", "A", NamespaceConfig.defaults(), T0, T0));
        repository.save(new Namespace("ns-3", "C", NamespaceConfig.defaults(), T0, T0));

        final List<Namespace> all = repository.findAll();
        assertThat(all).extracting(Namespace::id).containsExactly("ns-1", "ns-2", "ns-3");
    }

    @Test
    void deleteReturnsTrueOnlyWhenRowExisted() {
        assertThat(repository.delete("missing")).isFalse();

        repository.save(new Namespace("ns-1", "n", NamespaceConfig.defaults(), T0, T0));
        assertThat(repository.delete("ns-1")).isTrue();
        assertThat(repository.findById("ns-1")).isEmpty();
    }
}
