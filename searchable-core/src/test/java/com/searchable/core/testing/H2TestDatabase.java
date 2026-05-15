package com.searchable.core.testing;

import com.searchable.core.infrastructure.persistence.DataSourceFactory;
import com.searchable.core.infrastructure.persistence.PersistenceConfig;
import com.searchable.core.infrastructure.persistence.SchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local H2 in-memory fixture for searchable-core's own tests.
 *
 * <p>Mirrors the public {@code H2DatabaseFixture} in {@code searchable-testkit}
 * but lives inside {@code src/test/java} so that searchable-core can use it
 * without introducing a reactor cycle (testkit depends on core).
 */
public final class H2TestDatabase implements AutoCloseable {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final DataSource dataSource;

    private H2TestDatabase(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static H2TestDatabase open() {
        final String name = "core-test-" + COUNTER.incrementAndGet();
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "sa", ""));
        new SchemaInitializer(ds).initialize();
        return new H2TestDatabase(ds);
    }

    public DataSource dataSource() { return dataSource; }

    @Override
    public void close() {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
