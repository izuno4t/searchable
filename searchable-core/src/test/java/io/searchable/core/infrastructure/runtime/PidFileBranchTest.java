package io.searchable.core.infrastructure.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage supplement for {@link PidFile} error paths: directory creation
 * failure (line 52-53 in source) and PID-file write failure (line 66-67).
 * Triggered by removing write permission on the parent directory; on
 * systems that ignore POSIX permissions the tests are skipped.
 */
class PidFileBranchTest {

    @TempDir Path tempDir;

    @Test
    void openWrapsDirectoryCreationFailureInUncheckedIo() throws Exception {
        // Make tempDir read-only so PidFile.directoryFor + Files.createDirectories
        // both fail with IOException → wrapped into UncheckedIOException.
        org.junit.jupiter.api.Assumptions.assumeTrue(
            Files.getFileStore(tempDir).supportsFileAttributeView("posix"),
            "POSIX file attributes required for permission flip");
        final Set<PosixFilePermission> original = Files.getPosixFilePermissions(tempDir);
        try {
            // Read + execute only — no write.
            Files.setPosixFilePermissions(tempDir, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE));

            assertThatThrownBy(() -> PidFile.open(tempDir, "blocked"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("PID directory");
        } finally {
            Files.setPosixFilePermissions(tempDir, original);
        }
    }

    @Test
    void openOverwritesExistingPidFileWithLivePid() throws Exception {
        // Pre-create a PID file with this process's own PID so it is BOTH
        // > 0 and reported live by ProcessHandle.of(). Hits the warn branch.
        final Path pidDir = PidFile.directoryFor(tempDir);
        Files.createDirectories(pidDir);
        final long ownPid = ProcessHandle.current().pid();
        Files.writeString(pidDir.resolve("dup.pid"), Long.toString(ownPid));

        final PidFile reopened = PidFile.open(tempDir, "dup");
        try {
            assertThat(reopened.pid()).isEqualTo(ownPid);
            assertThat(Files.readString(reopened.path())).isEqualTo(Long.toString(ownPid));
        } finally {
            reopened.close();
        }
    }

    @Test
    void openWrapsWriteFailureInUncheckedIo() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            Files.getFileStore(tempDir).supportsFileAttributeView("posix"),
            "POSIX file attributes required for permission flip");
        // Create the pids dir up-front, then make it read-only so the
        // PID-file write (line 65) fails with IOException → wrapped.
        final Path pidDir = PidFile.directoryFor(tempDir);
        Files.createDirectories(pidDir);
        final Set<PosixFilePermission> original = Files.getPosixFilePermissions(pidDir);
        try {
            Files.setPosixFilePermissions(pidDir, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE));

            assertThatThrownBy(() -> PidFile.open(tempDir, "blocked"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("PID file");
        } finally {
            Files.setPosixFilePermissions(pidDir, original);
        }
    }
}
