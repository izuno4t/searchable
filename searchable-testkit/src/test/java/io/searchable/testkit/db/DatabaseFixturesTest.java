package io.searchable.testkit.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseFixturesTest {

    @Test
    void h2AndPostgresAlwaysIncludesBothRows() {
        final List<Arguments> rows = DatabaseFixtures.h2AndPostgres().toList();
        final List<String> labels = rows.stream()
            .map(a -> (String) a.get()[0])
            .collect(Collectors.toList());

        assertThat(labels).containsExactly("H2", "PostgreSQL");
        assertThat(rows).allSatisfy(a -> {
            assertThat(a.get()).hasSize(2);
            assertThat(a.get()[1]).isInstanceOf(Supplier.class);
        });
    }

    @Test
    void h2AndPostgresIfDockerAvailableAlwaysIncludesH2() {
        final List<String> labels = DatabaseFixtures.h2AndPostgresIfDockerAvailable()
            .map(a -> (String) a.get()[0])
            .toList();

        assertThat(labels).contains("H2");
        // PostgreSQL inclusion depends on the host's Docker availability; we
        // only assert the H2 row always exists so the test is hermetic.
        assertThat(labels).allSatisfy(l ->
            assertThat(l).isIn("H2", "PostgreSQL"));
    }

    @Test
    void h2SupplierOpensWorkingFixture() {
        final Arguments h2Row = DatabaseFixtures.h2AndPostgres()
            .filter(a -> "H2".equals(a.get()[0]))
            .findFirst()
            .orElseThrow();

        @SuppressWarnings("unchecked")
        final Supplier<DatabaseFixture> supplier = (Supplier<DatabaseFixture>) h2Row.get()[1];
        try (DatabaseFixture fx = supplier.get()) {
            assertThat(fx.label()).isEqualTo("H2");
            assertThat(fx.dataSource()).isNotNull();
        }
    }
}
