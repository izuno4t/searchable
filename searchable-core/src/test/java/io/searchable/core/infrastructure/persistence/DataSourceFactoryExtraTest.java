package io.searchable.core.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceFactoryExtraTest {

    @Test
    void h2ReadOnlyWithoutAccessModeRLogsButStillBuilds() {
        final PersistenceConfig cfg = new PersistenceConfig(
            "H2", "jdbc:h2:mem:dsf-ro;DB_CLOSE_DELAY=-1", "sa", "");
        // Should not throw even though the URL is missing ACCESS_MODE_DATA=r.
        final var ds = DataSourceFactory.create(cfg, true);
        assertThat(ds).isNotNull();
    }

    @Test
    void h2ReadOnlyWithAccessModeRSkipsWarning() {
        final PersistenceConfig cfg = new PersistenceConfig(
            "H2", "jdbc:h2:mem:dsf-ro2;DB_CLOSE_DELAY=-1;ACCESS_MODE_DATA=r", "sa", "");
        final var ds = DataSourceFactory.create(cfg, true);
        assertThat(ds).isNotNull();
    }

    @Test
    void hikariConfigWithoutDriverDoesNotSetDriverClassName() {
        final PersistenceConfig cfg = new PersistenceConfig(
            "JDBC", "jdbc:h2:mem:hc-no-driver;DB_CLOSE_DELAY=-1", "sa", "");
        final HikariConfig hc = DataSourceFactory.buildHikariConfig(cfg, null, "p");
        assertThat(hc.getDriverClassName()).isNull();
        assertThat(hc.isReadOnly()).isFalse();
    }
}
