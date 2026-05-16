package io.searchable.core.application;

import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Restores namespace Lucene indexes previously written by
 * {@link BackupService#snapshot(Path)}.
 *
 * <p>For each namespace present in the backup, the current on-disk index
 * directory is removed and replaced with the snapshot contents. Open
 * Lucene contexts are evicted from the provider so the next acquisition
 * re-opens the restored data.
 */
public final class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    private final LuceneIndexProvider indexProvider;
    private final IndexLayout layout;

    public RestoreService(final LuceneIndexProvider indexProvider, final IndexLayout layout) {
        this.indexProvider = Objects.requireNonNull(indexProvider);
        this.layout = Objects.requireNonNull(layout);
    }

    /** Restore every namespace present in the backup directory. */
    public List<String> restoreAll(final Path source) {
        Objects.requireNonNull(source, "source must not be null");
        final Path indexes = source.resolve("indexes");
        if (!Files.isDirectory(indexes)) {
            throw new IllegalStateException(
                "Backup does not contain an 'indexes' directory: " + source);
        }
        final List<String> restored = new ArrayList<>();
        try (var stream = Files.list(indexes)) {
            stream.filter(Files::isDirectory).forEach(p -> {
                final String ns = p.getFileName().toString();
                restoreOne(source, ns);
                restored.add(ns);
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enumerate backup at " + source, e);
        }
        log.info("restore completed: {} namespace(s) from {}", restored.size(), source);
        return List.copyOf(restored);
    }

    /** Restore a single namespace from the backup. */
    public void restoreOne(final Path source, final String namespaceId) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        final Path backupDir = source.resolve("indexes").resolve(namespaceId);
        if (!Files.isDirectory(backupDir)) {
            throw new IllegalStateException(
                "Backup does not contain namespace " + namespaceId + " at " + backupDir);
        }
        // Evict any open writer/searcher and wipe the live directory.
        try {
            indexProvider.remove(namespaceId, true);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to clear existing index for " + namespaceId, e);
        }
        final Path target = layout.directoryFor(namespaceId);
        try {
            Files.createDirectories(target.getParent());
            FileUtils.copyDirectory(backupDir.toFile(), target.toFile());
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to copy backup for " + namespaceId + " to " + target, e);
        }
        log.info("restored namespace {} from {}", namespaceId, backupDir);
    }
}
