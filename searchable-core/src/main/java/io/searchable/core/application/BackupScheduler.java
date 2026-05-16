package io.searchable.core.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fires {@link BackupService#snapshot(Path)} on a fixed interval.
 *
 * <p>Each invocation creates a new sub-directory under {@code rootTarget}
 * named with the snapshot timestamp so successive backups do not
 * overwrite each other. The scheduler retains only the {@code keep}
 * most recent snapshots; older ones are deleted on a best-effort basis.
 */
public final class BackupScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final Path rootTarget;
    private final int keep;
    private final ScheduledExecutorService scheduler;

    public BackupScheduler(final BackupService backupService,
                           final Path rootTarget,
                           final int keep) {
        this.backupService = Objects.requireNonNull(backupService);
        this.rootTarget = Objects.requireNonNull(rootTarget);
        if (keep <= 0) {
            throw new IllegalArgumentException("keep must be positive, was " + keep);
        }
        this.keep = keep;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "searchable-backup-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** Begin periodic backups, first one fires after {@code interval}. */
    public void start(final Duration interval) {
        Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        scheduler.scheduleAtFixedRate(this::runOnce,
            interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("backup scheduler started: every {}, retention {}", interval, keep);
    }

    /** Trigger a backup immediately. Visible for tests. */
    public Path runOnce() {
        final Path target = rootTarget.resolve("snapshot-" + Instant.now().toString()
            .replace(':', '-'));
        backupService.snapshot(target);
        prune();
        return target;
    }

    private void prune() {
        try (var stream = java.nio.file.Files.list(rootTarget)) {
            final java.util.List<Path> snapshots = stream
                .filter(java.nio.file.Files::isDirectory)
                .filter(p -> p.getFileName().toString().startsWith("snapshot-"))
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
            for (int i = keep; i < snapshots.size(); i++) {
                try {
                    org.apache.commons.io.FileUtils.deleteDirectory(snapshots.get(i).toFile());
                    log.info("pruned old snapshot {}", snapshots.get(i));
                } catch (java.io.IOException e) {
                    log.warn("failed to prune snapshot {}", snapshots.get(i), e);
                }
            }
        } catch (java.io.IOException e) {
            log.warn("failed to list snapshots under {}", rootTarget, e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
