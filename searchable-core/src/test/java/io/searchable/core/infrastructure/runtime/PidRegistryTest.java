package io.searchable.core.infrastructure.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage for {@link PidRegistry}. Real {@code kill -HUP} delivery is hard
 * to exercise without spawning a sacrificial child process, so this test
 * focuses on the path-discovery / stale-entry / empty-state branches that
 * make up the bulk of the class.
 */
class PidRegistryTest {

    @TempDir Path tempDir;

    @Test
    void constructorRejectsNullBaseDir() {
        assertThatThrownBy(() -> new PidRegistry(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registeredCountReturnsZeroWhenDirectoryAbsent() {
        final PidRegistry r = new PidRegistry(tempDir);
        assertThat(r.registeredCount()).isZero();
    }

    @Test
    void broadcastSighupReturnsZeroWhenDirectoryAbsent() {
        final PidRegistry r = new PidRegistry(tempDir);
        assertThat(r.broadcastSighup()).isZero();
    }

    @Test
    void registeredCountCountsAllPidFilesInDirectory() throws Exception {
        final Path dir = PidFile.directoryFor(tempDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("a.pid"), "11111");
        Files.writeString(dir.resolve("b.pid"), "22222");
        Files.writeString(dir.resolve("not-a-pid.txt"), "should-be-ignored");

        final PidRegistry r = new PidRegistry(tempDir);
        assertThat(r.registeredCount()).isEqualTo(2);
    }

    @Test
    void broadcastSighupRemovesStalePidFilesOnUnix() throws Exception {
        // Skip on Windows: PidRegistry no-ops there, so the stale-cleanup path
        // is unreachable.
        org.junit.jupiter.api.Assumptions.assumeFalse(
            System.getProperty("os.name", "").toLowerCase().contains("win"));

        final Path dir = PidFile.directoryFor(tempDir);
        Files.createDirectories(dir);
        // Use a PID well beyond the typical pid_max so ProcessHandle.of()
        // reliably returns empty. Critically we do NOT use 0 — `kill -HUP 0`
        // signals the caller's process group and would tear down the test
        // JVM along with surefire.
        final long deadPid = Integer.MAX_VALUE - 1;
        final Path stale = dir.resolve("stale.pid");
        Files.writeString(stale, Long.toString(deadPid));

        final PidRegistry r = new PidRegistry(tempDir);
        final int sent = r.broadcastSighup();

        assertThat(sent).isZero();
        assertThat(stale).doesNotExist();
    }

    @Test
    void broadcastSighupIgnoresMalformedPidFile() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
            System.getProperty("os.name", "").toLowerCase().contains("win"));

        final Path dir = PidFile.directoryFor(tempDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("bad.pid"), "not-a-number");

        final PidRegistry r = new PidRegistry(tempDir);
        assertThat(r.broadcastSighup()).isZero();
    }

    @Test
    void broadcastSighupSkipsOnNonUnixPlatform() throws Exception {
        // Temporarily flip os.name to a Windows-ish value so the constructor
        // sets unixLike=false and the early-return branch fires.
        final String prev = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 99");
        try {
            final Path dir = PidFile.directoryFor(tempDir);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("anything.pid"), "12345");

            final PidRegistry r = new PidRegistry(tempDir);
            assertThat(r.broadcastSighup()).isZero();
        } finally {
            if (prev == null) System.clearProperty("os.name");
            else System.setProperty("os.name", prev);
        }
    }

    @Test
    void registeredCountReportsZeroAfterRegistrySeesEmptyDir() throws Exception {
        final Path dir = PidFile.directoryFor(tempDir);
        Files.createDirectories(dir); // empty directory
        final PidRegistry r = new PidRegistry(tempDir);
        assertThat(r.registeredCount()).isZero();
    }
}
