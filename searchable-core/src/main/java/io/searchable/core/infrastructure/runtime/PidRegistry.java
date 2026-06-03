package io.searchable.core.infrastructure.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Discovers PID files written by {@link PidFile} under
 * {@code <baseDir>/pids/} and sends {@code SIGHUP} to each live PID.
 *
 * <p>Used by the CLI after an ingest commit so apps holding the shared
 * data directory open refresh their Lucene readers.
 *
 * <p>Stale PID files (whose PID is not alive) are best-effort removed.
 * On Windows the broadcast is a no-op.
 */
public final class PidRegistry {

    private static final Logger log = LoggerFactory.getLogger(PidRegistry.class);
    private static final String EXT = ".pid";

    private final Path pidDir;
    private final boolean unixLike;

    public PidRegistry(final Path baseDir) {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        this.pidDir = PidFile.directoryFor(baseDir);
        this.unixLike = !System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Send {@code SIGHUP} to every live PID registered under the data
     * directory. Returns the count of successful sends. Stale entries
     * are removed; a missing directory yields {@code 0} without warning.
     */
    public int broadcastSighup() {
        if (!unixLike) {
            log.debug("SIGHUP broadcast skipped: non-Unix platform");
            return 0;
        }
        if (!Files.isDirectory(pidDir)) {
            return 0;
        }
        int sent = 0;
        for (final PidEntry e : listEntries()) {
            if (ProcessHandle.of(e.pid).isEmpty()) {
                log.debug("removing stale PID file {} (pid {} not alive)", e.file, e.pid);
                try {
                    Files.deleteIfExists(e.file);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
                continue;
            }
            if (sendSighup(e.pid)) {
                log.info("sent SIGHUP to {} (pid={})", appNameOf(e.file), e.pid);
                sent++;
            }
        }
        return sent;
    }

    /** Number of PID files under the registry directory (alive or not). */
    public int registeredCount() {
        if (!Files.isDirectory(pidDir)) {
            return 0;
        }
        return listEntries().size();
    }

    private boolean sendSighup(final long pid) {
        try {
            final Process p = new ProcessBuilder("kill", "-HUP", Long.toString(pid))
                .redirectErrorStream(true)
                .start();
            final int rc = p.waitFor();
            if (rc != 0) {
                log.warn("kill -HUP {} returned {}", pid, rc);
                return false;
            }
            return true;
        } catch (IOException ex) {
            log.warn("Failed to send SIGHUP to pid {}: {}", pid, ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted sending SIGHUP to pid {}", pid);
            return false;
        }
    }

    private List<PidEntry> listEntries() {
        final List<PidEntry> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pidDir, "*" + EXT)) {
            for (final Path p : stream) {
                PidFile.readPid(p).ifPresent(pid -> out.add(new PidEntry(p, pid)));
            }
        } catch (NoSuchFileException ignored) {
            // directory disappeared between isDirectory() and stream open
        } catch (IOException e) {
            log.warn("Failed to list PID directory {}: {}", pidDir, e.getMessage());
        }
        return out;
    }

    private static String appNameOf(final Path file) {
        final String name = file.getFileName().toString();
        return name.endsWith(EXT) ? name.substring(0, name.length() - EXT.length()) : name;
    }

    private record PidEntry(Path file, long pid) { }
}
