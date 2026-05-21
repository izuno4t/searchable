package io.searchable.core.application;

import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexContext;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Takes file-system snapshots of namespace Lucene indexes.
 *
 * <p>A backup target is a directory structured as:
 * <pre>
 *   target/
 *     manifest.txt           ← list of namespaces + timestamp
 *     indexes/&lt;namespaceId&gt;/  ← copied Lucene segments
 * </pre>
 *
 * <p>The service flushes pending writes before copying so the on-disk
 * segments reflect every committed document. Concurrent writes during the
 * copy are <em>not</em> blocked; if strict point-in-time consistency is
 * required, callers must coordinate at a higher layer (e.g. quiescing the
 * ingest queue before invoking {@link #snapshot(Path)}).
 */
public final class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final LuceneIndexProvider indexProvider;
    private final IndexLayout layout;
    private final Clock clock;

    public BackupService(final LuceneIndexProvider indexProvider,
                         final IndexLayout layout) {
        this(indexProvider, layout, Clock.systemUTC());
    }

    public BackupService(final LuceneIndexProvider indexProvider,
                         final IndexLayout layout,
                         final Clock clock) {
        this.indexProvider = Objects.requireNonNull(indexProvider);
        this.layout = Objects.requireNonNull(layout);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Snapshot every currently-open namespace into {@code target}. */
    public BackupSummary snapshot(final Path target) {
        return snapshot(target, openNamespaces());
    }

    /** Snapshot only the specified namespaces. */
    public BackupSummary snapshot(final Path target, final List<String> namespaceIds) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(namespaceIds, "namespaceIds must not be null");
        try {
            Files.createDirectories(target.resolve("indexes"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create backup directory " + target, e);
        }

        long bytes = 0L;
        for (final String namespaceId : namespaceIds) {
            bytes += snapshotOne(target, namespaceId);
        }

        final java.time.Instant taken = clock.instant();
        try {
            Files.writeString(target.resolve("manifest.txt"),
                "takenAt=" + taken + "\nnamespaces=" + String.join(",", namespaceIds) + '\n');
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write backup manifest", e);
        }
        log.info("backup completed: {} namespace(s), {} bytes -> {}",
            namespaceIds.size(), bytes, target);
        return new BackupSummary(taken, List.copyOf(namespaceIds), bytes);
    }

    private long snapshotOne(final Path target, final String namespaceId) {
        // Flush so all committed segments are durable before we copy.
        if (indexProvider.isOpen(namespaceId)) {
            final LuceneIndexContext ctx = indexProvider.getOrCreate(namespaceId);
            try {
                if (!ctx.isReadOnly()) {
                    ctx.writer().commit();
                }
                ctx.refresh();
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Failed to flush namespace " + namespaceId + " before backup", e);
            }
        }
        // Snapshot only the latest readable version; `.tmp/` builds and
        // obsolete versions are ignored. The backup directory layout
        // intentionally does not retain the source version timestamp so
        // a restore can re-stamp under a fresh timestamp on the target
        // side (see RestoreService).
        final Path source = layout.latestReadable(namespaceId).orElse(null);
        if (source == null || !Files.isDirectory(source)) {
            log.warn("namespace {} has no readable index version under {}; skipping",
                namespaceId, layout.namespaceDir(namespaceId));
            return 0L;
        }
        final Path dest = target.resolve("indexes").resolve(namespaceId);
        try {
            FileUtils.copyDirectory(source.toFile(), dest.toFile());
            return FileUtils.sizeOfDirectory(dest.toFile());
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to copy index for " + namespaceId + " to " + dest, e);
        }
    }

    private List<String> openNamespaces() {
        // Probe the layout for actual directories on disk.
        final Path root = layout.rootDirectory();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enumerate namespaces under " + root, e);
        }
    }

    /** Result of a {@link #snapshot(Path)} call. */
    public record BackupSummary(java.time.Instant takenAt,
                                List<String> namespaceIds,
                                long totalBytes) { }
}
