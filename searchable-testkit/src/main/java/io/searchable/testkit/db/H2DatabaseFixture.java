package io.searchable.testkit.db;

import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * H2 in-memory database fixture with {@code MODE=PostgreSQL} compatibility.
 *
 * <p>Each instance gets a unique database name so that parallel tests do not
 * collide. The schema is initialized eagerly through {@link SchemaInitializer}.
 */
public final class H2DatabaseFixture implements DatabaseFixture {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final DataSource dataSource;
    private final PersistenceConfig config;

    private H2DatabaseFixture(final DataSource dataSource, final PersistenceConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    /** In-memory mode with a fresh per-invocation database name. */
    public static H2DatabaseFixture inMemory() {
        final String name = "testkit-" + COUNTER.incrementAndGet();
        return create(new PersistenceConfig("H2",
            "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", ""));
    }

    /** File-backed mode under the given directory. */
    public static H2DatabaseFixture fileBacked(final Path directory) {
        return create(new PersistenceConfig("H2",
            "jdbc:h2:" + directory.resolve("testkit") + ";MODE=PostgreSQL", "sa", ""));
    }

    private static H2DatabaseFixture create(final PersistenceConfig config) {
        final DataSource ds = DataSourceFactory.create(config);
        new SchemaInitializer(ds).initialize();
        return new H2DatabaseFixture(ds, config);
    }

    @Override public DataSource dataSource() { return dataSource; }
    @Override public PersistenceConfig config() { return config; }
    @Override public String label() { return "H2"; }

    @Override
    public void close() {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        } catch (Exception ignored) {
            // best-effort; tests rely on the JVM to release the in-memory store
        }
    }
}
