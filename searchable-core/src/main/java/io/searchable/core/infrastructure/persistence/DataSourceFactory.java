package io.searchable.core.infrastructure.persistence;

import org.h2.jdbcx.JdbcConnectionPool;

import javax.sql.DataSource;
import java.util.Locale;

/**
 * Creates {@link DataSource} instances from {@link PersistenceConfig}.
 *
 * <p>Phase 1 supports H2 only; other RDBMS implementations may be added later
 * by extending the {@code switch} on {@code type}.
 */
public final class DataSourceFactory {

    private DataSourceFactory() { }

    public static DataSource create(final PersistenceConfig config) {
        final String type = config.type().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "H2" -> {
                final JdbcConnectionPool pool = JdbcConnectionPool.create(
                    config.url(), config.username(), config.password());
                pool.setMaxConnections(16);
                yield pool;
            }
            default -> throw new IllegalArgumentException(
                "Unsupported persistence type: " + config.type());
        };
    }
}
