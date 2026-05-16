package io.searchable.core.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceFactoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Test
    void h2TypeReturnsJdbcConnectionPool() throws Exception {
        final DataSource ds = DataSourceFactory.create(h2Config());
        try (Connection c = ds.getConnection()) {
            assertThat(c.isValid(1)).isTrue();
        }
        assertThat(ds).isInstanceOf(JdbcConnectionPool.class);
        ((JdbcConnectionPool) ds).dispose();
    }

    @Test
    void jdbcTypeReturnsHikariDataSourceAndConnects() throws Exception {
        final PersistenceConfig config = new PersistenceConfig(
            "JDBC", uniqueH2InMemoryUrl(), "sa", "", 4);
        try (HikariDataSource ds = (HikariDataSource) DataSourceFactory.create(config);
             Connection c = ds.getConnection()) {
            assertThat(c.isValid(1)).isTrue();
            assertThat(ds.getMaximumPoolSize()).isEqualTo(4);
            assertThat(ds.getPoolName()).isEqualTo("Searchable-JDBC");
        }
    }

    @Test
    void postgresqlTypeWiresDriverAndPoolName() {
        // Connecting requires a live PostgreSQL server (covered by TASK-013);
        // verify only that the factory wires the driver and pool name correctly
        // by inspecting the pre-built HikariConfig (avoids opening connections).
        final PersistenceConfig config = new PersistenceConfig(
            "POSTGRESQL",
            "jdbc:postgresql://db.example.invalid:5432/searchable",
            "user", "pw", 8);
        final com.zaxxer.hikari.HikariConfig hc = DataSourceFactory.buildHikariConfig(
            config, DataSourceFactory.POSTGRESQL_DRIVER, DataSourceFactory.POSTGRESQL_POOL_NAME);

        assertThat(hc.getDriverClassName()).isEqualTo("org.postgresql.Driver");
        assertThat(hc.getPoolName()).isEqualTo("Searchable-PostgreSQL");
        assertThat(hc.getMaximumPoolSize()).isEqualTo(8);
        assertThat(hc.getJdbcUrl()).isEqualTo(config.url());
        assertThat(hc.getUsername()).isEqualTo("user");
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> DataSourceFactory.create(new PersistenceConfig(
                "MYSQL", "jdbc:mysql://localhost", "u", "p")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MYSQL");
    }

    @Test
    void persistenceConfigAppliesDefaultPoolSizeWhenZero() {
        final PersistenceConfig cfg = new PersistenceConfig("H2", "jdbc:h2:mem:x", "sa", "", 0);
        assertThat(cfg.maxPoolSize()).isEqualTo(PersistenceConfig.DEFAULT_POOL_SIZE);
    }

    private PersistenceConfig h2Config() {
        return new PersistenceConfig("H2", uniqueH2InMemoryUrl(), "sa", "");
    }

    private String uniqueH2InMemoryUrl() {
        return "jdbc:h2:mem:dsf-" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
    }
}
