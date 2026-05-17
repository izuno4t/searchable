package io.searchable.testkit.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class H2DatabaseFixtureTest {

    @TempDir Path tempDir;

    @Test
    void fileBackedFixtureExposesUrlPointingAtGivenDirectory() throws Exception {
        try (H2DatabaseFixture fx = H2DatabaseFixture.fileBacked(tempDir)) {
            assertThat(fx.label()).isEqualTo("H2");
            assertThat(fx.config().type()).isEqualTo("H2");
            assertThat(fx.config().url())
                .startsWith("jdbc:h2:")
                .contains(tempDir.toString())
                .contains("MODE=PostgreSQL");
            try (Connection c = fx.dataSource().getConnection()) {
                assertThat(c.isClosed()).isFalse();
            }
        }
    }

    @Test
    void inMemoryFixturesProduceDistinctUrls() {
        try (H2DatabaseFixture a = H2DatabaseFixture.inMemory();
             H2DatabaseFixture b = H2DatabaseFixture.inMemory()) {
            assertThat(a.config().url()).isNotEqualTo(b.config().url());
        }
    }

    @Test
    void closeIsBestEffortAndDoesNotThrow() {
        final H2DatabaseFixture fx = H2DatabaseFixture.inMemory();
        // Closing twice exercises the swallowed-exception branch.
        fx.close();
        fx.close();
    }
}
