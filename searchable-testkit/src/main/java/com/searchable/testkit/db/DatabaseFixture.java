package com.searchable.testkit.db;

import com.searchable.core.infrastructure.persistence.PersistenceConfig;

import javax.sql.DataSource;

/**
 * A test-scoped database fixture.
 *
 * <p>Implementations provide a {@link DataSource} and corresponding
 * {@link PersistenceConfig} backed by an H2 in-memory database
 * ({@link H2DatabaseFixture}) or by a real PostgreSQL container
 * ({@link PostgresDatabaseFixture}). Tests should call {@link #close()} in
 * an {@code @AfterEach} / {@code @AfterAll} hook to release resources.
 *
 * @see DatabaseFixtures for parameterized test support
 */
public interface DatabaseFixture extends AutoCloseable {

    DataSource dataSource();

    PersistenceConfig config();

    /** Short identifier used in {@code @ParameterizedTest} display names. */
    String label();

    @Override
    void close();
}
