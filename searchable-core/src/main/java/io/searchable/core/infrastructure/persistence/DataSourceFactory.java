package io.searchable.core.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcConnectionPool;

import javax.sql.DataSource;
import java.util.Locale;

/**
 * Creates {@link DataSource} instances from {@link PersistenceConfig}.
 *
 * <p>Supported types:
 * <ul>
 *   <li>{@code H2} -- H2 embedded (file or in-memory) backed by
 *       {@link JdbcConnectionPool}.</li>
 *   <li>{@code POSTGRESQL} -- PostgreSQL via HikariCP.</li>
 *   <li>{@code JDBC} -- generic JDBC URL via HikariCP. The driver class is
 *       inferred from the URL prefix (e.g. {@code jdbc:h2:tcp://...} for
 *       H2 server mode).</li>
 * </ul>
 *
 * <p>When the type is not recognized, an {@link IllegalArgumentException}
 * is thrown so misconfiguration fails fast.
 */
public final class DataSourceFactory {

    static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";
    static final String POSTGRESQL_POOL_NAME = "Searchable-PostgreSQL";
    static final String JDBC_POOL_NAME = "Searchable-JDBC";

    private DataSourceFactory() { }

    public static DataSource create(final PersistenceConfig config) {
        final String type = config.type().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "H2" -> createH2(config);
            case "POSTGRESQL" -> new HikariDataSource(
                buildHikariConfig(config, POSTGRESQL_DRIVER, POSTGRESQL_POOL_NAME));
            case "JDBC" -> new HikariDataSource(
                buildHikariConfig(config, null, JDBC_POOL_NAME));
            default -> throw new IllegalArgumentException(
                "Unsupported persistence type: " + config.type());
        };
    }

    private static DataSource createH2(final PersistenceConfig config) {
        final JdbcConnectionPool pool = JdbcConnectionPool.create(
            config.url(), config.username(), config.password());
        pool.setMaxConnections(config.maxPoolSize());
        return pool;
    }

    /** Visible for tests. Builds a HikariConfig without opening connections. */
    static HikariConfig buildHikariConfig(final PersistenceConfig config,
                                          final String driverClassName,
                                          final String poolName) {
        final HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.url());
        hc.setUsername(config.username());
        hc.setPassword(config.password());
        hc.setMaximumPoolSize(config.maxPoolSize());
        hc.setPoolName(poolName);
        if (driverClassName != null) {
            hc.setDriverClassName(driverClassName);
        }
        return hc;
    }
}
