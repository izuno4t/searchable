package io.searchable.core.infrastructure.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Coverage for {@link PidFile}: open/close lifecycle and {@code readPid} parsing branches. */
class PidFileTest {

    @TempDir Path tempDir;

    @Test
    void openCreatesDirectoryAndWritesCurrentPid() throws Exception {
        try (PidFile pf = PidFile.open(tempDir, "test-app")) {
            assertThat(pf.pid()).isEqualTo(ProcessHandle.current().pid());
            assertThat(pf.path()).exists();
            final long stored = Long.parseLong(
                Files.readString(pf.path(), StandardCharsets.UTF_8).strip());
            assertThat(stored).isEqualTo(pf.pid());
        }
    }

    @Test
    void closeRemovesPidFile() {
        final PidFile pf = PidFile.open(tempDir, "removable");
        final Path file = pf.path();
        assertThat(file).exists();
        pf.close();
        assertThat(file).doesNotExist();
    }

    @Test
    void closeIsIdempotent() {
        final PidFile pf = PidFile.open(tempDir, "idempotent");
        pf.close();
        // Second close on a non-existent file must not throw.
        pf.close();
    }

    @Test
    void openOverwritesAnExistingDeadPidFile() throws Exception {
        // Pre-existing PID file with a clearly dead PID.
        final Path dir = PidFile.directoryFor(tempDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("preexisting.pid"), "1");

        try (PidFile pf = PidFile.open(tempDir, "preexisting")) {
            assertThat(pf.pid()).isEqualTo(ProcessHandle.current().pid());
        }
    }

    @Test
    void openOverwritesWarningWhenLivePidPresent() throws Exception {
        final Path dir = PidFile.directoryFor(tempDir);
        Files.createDirectories(dir);
        final long alive = ProcessHandle.current().pid();
        Files.writeString(dir.resolve("alive.pid"), Long.toString(alive));

        try (PidFile pf = PidFile.open(tempDir, "alive")) {
            // Was overwritten with the same value — still alive.
            assertThat(pf.pid()).isEqualTo(alive);
        }
    }

    @Test
    void readPidReturnsEmptyForEmptyFile() throws Exception {
        final Path empty = tempDir.resolve("empty.pid");
        Files.writeString(empty, "");
        assertThat(PidFile.readPid(empty)).isEmpty();
    }

    @Test
    void readPidReturnsEmptyForMalformedContent() throws Exception {
        final Path mal = tempDir.resolve("malformed.pid");
        Files.writeString(mal, "not-a-number");
        assertThat(PidFile.readPid(mal)).isEmpty();
    }

    @Test
    void readPidReturnsEmptyForMissingFile() {
        final Optional<Long> v = PidFile.readPid(tempDir.resolve("never-existed.pid"));
        assertThat(v).isEmpty();
    }

    @Test
    void readPidParsesValidNumeric() throws Exception {
        final Path valid = tempDir.resolve("valid.pid");
        Files.writeString(valid, "12345");
        assertThat(PidFile.readPid(valid)).contains(12345L);
    }

    @Test
    void directoryForRejectsNullBase() {
        assertThatThrownBy(() -> PidFile.directoryFor(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void directoryForResolvesUnderBaseDir() {
        assertThat(PidFile.directoryFor(tempDir).getFileName().toString())
            .isEqualTo(PidFile.DEFAULT_DIRECTORY_NAME);
    }
}
