package io.searchable.core.infrastructure.persistence.jdbc;

import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.testing.H2TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcNamespaceRepositoryExtraTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

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
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new JdbcNamespaceRepository(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void apiRejectsNullArgs() {
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.delete(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.exists(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void sqlErrorsWrappedAsIllegalStateAfterTableDropped() throws Exception {
        try (var c = db.dataSource().getConnection(); var s = c.createStatement()) {
            s.execute("DROP TABLE NAMESPACE CASCADE");
        }
        final Namespace ns = new Namespace("x", "n", NamespaceConfig.defaults(), T0, T0);
        assertThatThrownBy(() -> repository.save(ns)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.findById("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.findAll()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.delete("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.exists("x")).isInstanceOf(IllegalStateException.class);
    }
}
