package io.searchable.core.infrastructure.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@link PidRegistry#broadcastSighup()} success path by
 * spawning a {@code sleep} child process whose PID is recorded in the
 * registry directory. {@code SIGHUP} terminates the child without
 * affecting the test JVM, so we can observe the side effect (child exits)
 * without tearing down surefire.
 */
class PidRegistryBroadcastTest {

    @TempDir Path tempDir;

    @Test
    void broadcastSighupTerminatesChildProcessRegisteredAsPidFile() throws Exception {
        // The PidRegistry.broadcastSighup path that actually invokes
        // `kill -HUP <pid>` is only meaningful on Unix-family JVMs; on
        // Windows the registry no-ops at the platform check.
        Assumptions.assumeFalse(
            System.getProperty("os.name", "").toLowerCase().contains("win"));

        final Path dir = PidFile.directoryFor(tempDir);
        Files.createDirectories(dir);

        // sh -c 'sleep 60' so we have something whose PID we can SIGHUP.
        final Process child = new ProcessBuilder("sh", "-c", "sleep 60")
            .redirectErrorStream(true)
            .start();
        final long pid = child.pid();
        try {
            Files.writeString(dir.resolve("test-child.pid"), Long.toString(pid));

            final int sent = new PidRegistry(tempDir).broadcastSighup();

            assertThat(sent).isGreaterThanOrEqualTo(1);
            assertThat(child.waitFor(10, TimeUnit.SECONDS))
                .as("child should exit after receiving SIGHUP").isTrue();
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }
}
