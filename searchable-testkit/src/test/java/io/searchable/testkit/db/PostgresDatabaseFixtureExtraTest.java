package io.searchable.testkit.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresDatabaseFixtureExtraTest {

    @Test
    void startBootsContainerAndInitializesSchemaWhenDockerAvailable() throws Exception {
        assumeTrue(PostgresDatabaseFixture.isDockerAvailable(),
            "Docker not available – skipping container-based test");

        try (PostgresDatabaseFixture fx = PostgresDatabaseFixture.start()) {
            assertThat(fx.label()).isEqualTo("PostgreSQL");
            assertThat(fx.config().type()).isEqualTo("POSTGRESQL");
            assertThat(fx.config().url()).startsWith("jdbc:postgresql://");
            try (Connection c = fx.dataSource().getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT count(*) FROM information_schema.tables WHERE table_name='namespace'")) {
                rs.next();
                assertThat(rs.getInt(1)).isPositive();
            }
        }
    }

    @Test
    void apiAccessorsRejectNullImage() {
        // start(image) requires non-null image; passing null should fail fast.
        assertThatThrownBy(() -> PostgresDatabaseFixture.start(null))
            .isInstanceOf(NullPointerException.class);
    }
}
