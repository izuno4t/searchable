package io.searchable.core.application;

import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexContext;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Drives the residual IOException catch arms in BackupService / RestoreService / BackupScheduler. */
class BackupRestoreIoErrorTest {

    @TempDir Path tempDir;

    @Test
    void snapshotOneWrapsFlushFailure() throws Exception {
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final LuceneIndexContext ctx = mock(LuceneIndexContext.class);
        final IndexWriter writer = mock(IndexWriter.class);
        when(provider.isOpen("ns")).thenReturn(true);
        when(provider.getOrCreate("ns")).thenReturn(ctx);
        when(ctx.isReadOnly()).thenReturn(false);
        when(ctx.writer()).thenReturn(writer);
        doThrow(new IOException("commit-boom")).when(writer).commit();

        final var svc = new BackupService(provider, new IndexLayout(tempDir.resolve("idx")));
        assertThatThrownBy(() -> svc.snapshot(tempDir.resolve("out"), List.of("ns")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to flush namespace");
    }

    @Test
    void snapshotOneWrapsCopyDirectoryFailure() throws Exception {
        // Pre-seed a real source dir so we get past the Files.isDirectory
        // check and into the copy attempt.
        final IndexLayout layout = new IndexLayout(tempDir.resolve("src"));
        Files.createDirectories(layout.directoryFor("ns"));
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        when(provider.isOpen("ns")).thenReturn(false);

        try (MockedStatic<FileUtils> fu = mockStatic(FileUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            fu.when(() -> FileUtils.copyDirectory(any(java.io.File.class), any(java.io.File.class)))
                .thenThrow(new IOException("copy-boom"));
            final var svc = new BackupService(provider, layout);
            assertThatThrownBy(() -> svc.snapshot(tempDir.resolve("snap"), List.of("ns")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to copy index for");
        }
    }

    @Test
    void restoreOneWrapsCopyFailure() throws Exception {
        final Path backup = tempDir.resolve("bk");
        Files.createDirectories(backup.resolve("indexes/ns"));
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final IndexLayout layout = new IndexLayout(tempDir.resolve("live"));

        try (MockedStatic<FileUtils> fu = mockStatic(FileUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            fu.when(() -> FileUtils.copyDirectory(any(java.io.File.class), any(java.io.File.class)))
                .thenThrow(new IOException("restore-boom"));
            final var svc = new RestoreService(provider, layout);
            assertThatThrownBy(() -> svc.restoreOne(backup, "ns"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to copy backup");
        }
    }

    @Test
    void schedulerPruneWrapsListFailure() throws Exception {
        final Path root = tempDir.resolve("snaproot");
        Files.createDirectories(root);
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final IndexLayout layout = new IndexLayout(tempDir.resolve("li"));
        Files.createDirectories(layout.rootDirectory());
        final var backup = new BackupService(provider, layout);

        try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.list(root)).thenThrow(new IOException("list-boom"));
            try (var sch = new BackupScheduler(backup, root, 1)) {
                // runOnce -> snapshot succeeds (mocked files only affects
                // Files.list on root), then prune fires and catches the
                // synthetic IOException.
                sch.runOnce();
            }
        }
    }

    @Test
    void schedulerPruneSwallowsDeleteFailure() throws Exception {
        final Path root = tempDir.resolve("snaproot2");
        Files.createDirectories(root);
        // Seed two snapshots so prune actually tries to delete one when keep=1.
        Files.createDirectories(root.resolve("snapshot-old1"));
        Files.createDirectories(root.resolve("snapshot-old2"));
        final LuceneIndexProvider provider = mock(LuceneIndexProvider.class);
        final IndexLayout layout = new IndexLayout(tempDir.resolve("li2"));
        Files.createDirectories(layout.rootDirectory());
        final var backup = new BackupService(provider, layout);

        try (MockedStatic<FileUtils> fu = mockStatic(FileUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            fu.when(() -> FileUtils.deleteDirectory(any(java.io.File.class)))
                .thenThrow(new IOException("delete-boom"));
            try (var sch = new BackupScheduler(backup, root, 1)) {
                sch.runOnce();
            }
        }
    }
}
