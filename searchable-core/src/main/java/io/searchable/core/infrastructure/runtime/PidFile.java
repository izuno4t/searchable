package io.searchable.core.infrastructure.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes the running JVM's PID to {@code <baseDir>/pids/<appName>.pid}
 * on open and removes it on close.
 *
 * <p>The CLI scans the same directory after an ingest commit and sends
 * {@code SIGHUP} to each live PID so reader apps (webapp / api / mcp)
 * can refresh their Lucene index views without restarting.
 *
 * <p>If a PID file is already present with an alive PID, a warning is
 * logged and the file is overwritten — the caller is presumed to be a
 * replacement instance.
 */
public final class PidFile implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PidFile.class);

    /** Subdirectory under the data directory where PID files live. */
    public static final String DEFAULT_DIRECTORY_NAME = "pids";

    private final Path file;
    private final long pid;

    private PidFile(final Path file, final long pid) {
        this.file = file;
        this.pid = pid;
    }

    /** Resolve the directory that holds all PID files for {@code baseDir}. */
    public static Path directoryFor(final Path baseDir) {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        return baseDir.resolve(DEFAULT_DIRECTORY_NAME);
    }

    public static PidFile open(final Path baseDir, final String appName) {
        Objects.requireNonNull(appName, "appName must not be null");
        final Path dir = directoryFor(baseDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create PID directory " + dir, e);
        }
        final Path file = dir.resolve(appName + ".pid");
        final long pid = ProcessHandle.current().pid();
        if (Files.exists(file)) {
            final long prior = readPid(file).orElse(-1L);
            if (prior > 0 && ProcessHandle.of(prior).isPresent()) {
                log.warn("PID file {} already exists with live PID {} (overwriting)",
                    file, prior);
            }
        }
        try {
            Files.writeString(file, Long.toString(pid), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write PID file " + file, e);
        }
        log.info("wrote PID file {} (pid={})", file, pid);
        return new PidFile(file, pid);
    }

    public Path path() {
        return file;
    }

    public long pid() {
        return pid;
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(file);
            log.debug("removed PID file {}", file);
        } catch (IOException e) {
            log.warn("Failed to remove PID file {}", file, e);
        }
    }

    static Optional<Long> readPid(final Path file) {
        try {
            final String s = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (s.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(s));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
