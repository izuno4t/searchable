package com.searchable.testkit.db;

import com.searchable.core.infrastructure.persistence.DataSourceFactory;
import com.searchable.core.infrastructure.persistence.PersistenceConfig;
import com.searchable.core.infrastructure.persistence.SchemaInitializer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

/**
 * PostgreSQL fixture backed by a Testcontainers container.
 *
 * <p>Tests that need to verify behavior against the real production database
 * dialect use this fixture. The container starts on construction; call
 * {@link #close()} to stop it.
 *
 * <p>Requires Docker to be available on the test host. Tests that should run
 * even without Docker should branch on {@link #isDockerAvailable()}.
 */
public final class PostgresDatabaseFixture implements DatabaseFixture {

    private static final DockerImageName DEFAULT_IMAGE =
        DockerImageName.parse("postgres:16-alpine");

    private final PostgreSQLContainer<?> container;
    private final DataSource dataSource;
    private final PersistenceConfig config;

    private PostgresDatabaseFixture(final PostgreSQLContainer<?> container) {
        this.container = container;
        this.config = new PersistenceConfig("POSTGRESQL",
            container.getJdbcUrl(), container.getUsername(), container.getPassword());
        this.dataSource = DataSourceFactory.create(config);
        new SchemaInitializer(dataSource).initialize();
    }

    public static PostgresDatabaseFixture start() {
        return start(DEFAULT_IMAGE);
    }

    public static PostgresDatabaseFixture start(final DockerImageName image) {
        final PostgreSQLContainer<?> c = new PostgreSQLContainer<>(image)
            .withDatabaseName("searchable")
            .withUsername("searchable")
            .withPassword("searchable");
        c.start();
        return new PostgresDatabaseFixture(c);
    }

    /**
     * Cheap check whether Docker is reachable. Tests can skip
     * Postgres-backed cases when this returns {@code false}.
     */
    public static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public DataSource dataSource() { return dataSource; }
    @Override public PersistenceConfig config() { return config; }
    @Override public String label() { return "PostgreSQL"; }

    @Override
    public void close() {
        container.stop();
    }
}
