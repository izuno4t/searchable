package io.searchable.testkit.db;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class PostgresDatabaseFixtureTest {

    @Test
    void isDockerAvailableReturnsBooleanWithoutThrowing() {
        // Hermetic check: we don't assert true/false because both are valid
        // depending on the test host. We only assert the method itself is
        // safe to call (it catches all Throwables internally).
        assertThatCode(PostgresDatabaseFixture::isDockerAvailable).doesNotThrowAnyException();
    }
}
