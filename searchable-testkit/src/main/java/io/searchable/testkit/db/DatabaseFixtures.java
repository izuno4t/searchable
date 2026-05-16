package io.searchable.testkit.db;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;
import java.util.function.Supplier;

/**
 * Factory for {@link DatabaseFixture} instances and JUnit 5
 * {@code @MethodSource} providers.
 *
 * <p>Use {@link #h2AndPostgresIfDockerAvailable()} for repository-layer tests
 * that should cover both DB dialects:
 *
 * <pre>{@code
 * @ParameterizedTest(name = "{0}")
 * @MethodSource("io.searchable.testkit.db.DatabaseFixtures#h2AndPostgresIfDockerAvailable")
 * void test(String label, Supplier<DatabaseFixture> open) {
 *     try (DatabaseFixture fx = open.get()) { ... }
 * }
 * }</pre>
 */
public final class DatabaseFixtures {

    private DatabaseFixtures() { }

    /**
     * Two arguments per row: ({@code label}, {@code Supplier<DatabaseFixture>}).
     * The Postgres row is omitted when Docker is not available so tests still
     * exercise the H2 path on hosts without Docker.
     */
    public static Stream<Arguments> h2AndPostgresIfDockerAvailable() {
        final Stream.Builder<Arguments> b = Stream.builder();
        b.add(Arguments.of("H2", (Supplier<DatabaseFixture>) H2DatabaseFixture::inMemory));
        if (PostgresDatabaseFixture.isDockerAvailable()) {
            b.add(Arguments.of(
                "PostgreSQL", (Supplier<DatabaseFixture>) PostgresDatabaseFixture::start));
        }
        return b.build();
    }

    /** Always returns both rows. Tests that absolutely require Postgres use this. */
    public static Stream<Arguments> h2AndPostgres() {
        return Stream.of(
            Arguments.of("H2", (Supplier<DatabaseFixture>) H2DatabaseFixture::inMemory),
            Arguments.of("PostgreSQL", (Supplier<DatabaseFixture>) PostgresDatabaseFixture::start)
        );
    }
}
