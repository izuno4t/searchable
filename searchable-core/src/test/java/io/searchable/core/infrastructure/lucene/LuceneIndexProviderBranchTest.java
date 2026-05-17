package io.searchable.core.infrastructure.lucene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Branch coverage for {@link LuceneIndexProvider}. */
class LuceneIndexProviderBranchTest {

    @TempDir Path tempDir;

    @Test
    void constructorRejectsNullArgs() {
        final IndexLayout layout = new IndexLayout(tempDir);
        assertThatThrownBy(() -> new LuceneIndexProvider(null, AnalyzerFactory.japanese()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LuceneIndexProvider(layout, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese(), false, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void memoryAndReadOnlyAreMutuallyExclusive() {
        assertThatThrownBy(() -> new LuceneIndexProvider(
                new IndexLayout(tempDir), AnalyzerFactory.japanese(),
                true, StorageBackend.MEMORY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memoryBackendOpensWriterAndExposesBackend() {
        try (LuceneIndexProvider p = new LuceneIndexProvider(
                new IndexLayout(tempDir), AnalyzerFactory.japanese(),
                false, StorageBackend.MEMORY)) {
            assertThat(p.backend()).isEqualTo(StorageBackend.MEMORY);
            final LuceneIndexContext ctx = p.getOrCreate("mem-ns");
            assertThat(ctx).isNotNull();
            assertThat(p.isOpen("mem-ns")).isTrue();
        }
    }

    @Test
    void memoryBackendRemoveWithDeleteFilesSkipsFilesystemPath() throws Exception {
        try (LuceneIndexProvider p = new LuceneIndexProvider(
                new IndexLayout(tempDir), AnalyzerFactory.japanese(),
                false, StorageBackend.MEMORY)) {
            p.getOrCreate("mem-ns");
            p.remove("mem-ns", true); // exercises the "MEMORY backend" branch
            assertThat(p.isOpen("mem-ns")).isFalse();
        }
    }

    @Test
    void readOnlyThrowsForMissingNamespaceDirectory() {
        // First create an index dir on disk so we can later open read-only.
        final IndexLayout layout = new IndexLayout(tempDir);
        try (LuceneIndexProvider p = new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese(), true)) {
            assertThatThrownBy(() -> p.getOrCreate("never-existed"))
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void readOnlyThrowsForEmptyNamespaceDirectory() throws Exception {
        final IndexLayout layout = new IndexLayout(tempDir);
        Files.createDirectories(layout.directoryFor("empty-ns"));
        try (LuceneIndexProvider p = new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese(), true)) {
            assertThatThrownBy(() -> p.getOrCreate("empty-ns"))
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void readOnlyOpensExistingIndex() throws Exception {
        // Bootstrap an index in read-write mode, close it, then open read-only.
        final IndexLayout layout = new IndexLayout(tempDir);
        try (LuceneIndexProvider rw = new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese())) {
            rw.getOrCreate("ro-ns");
        }
        try (LuceneIndexProvider ro = new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese(), true)) {
            final LuceneIndexContext ctx = ro.getOrCreate("ro-ns");
            assertThat(ctx.isReadOnly()).isTrue();
        }
    }

    @Test
    void readOnlyRemoveWithDeleteFilesRejected() throws Exception {
        final IndexLayout layout = new IndexLayout(tempDir);
        try (LuceneIndexProvider rw = new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese())) {
            rw.getOrCreate("ro-rm");
        }
        try (LuceneIndexProvider ro = new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese(), true)) {
            ro.getOrCreate("ro-rm");
            assertThatThrownBy(() -> ro.remove("ro-rm", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
        }
    }

    @Test
    void removeOnUnknownNamespaceIsNoOp() throws Exception {
        try (LuceneIndexProvider p = new LuceneIndexProvider(
                new IndexLayout(tempDir), AnalyzerFactory.japanese())) {
            p.remove("never-opened", false);
            p.remove("never-opened", true);
        }
    }

    @Test
    void refreshAnalyzerRebuildsContext() throws Exception {
        try (LuceneIndexProvider p = new LuceneIndexProvider(
                new IndexLayout(tempDir), AnalyzerFactory.japanese())) {
            final LuceneIndexContext first = p.getOrCreate("rl-ns");
            p.refreshAnalyzer("rl-ns");
            final LuceneIndexContext second = p.getOrCreate("rl-ns");
            assertThat(second).isNotSameAs(first);
        }
    }

    @Test
    void refreshAnalyzerOnUnopenedNamespaceJustCreatesIt() throws Exception {
        try (LuceneIndexProvider p = new LuceneIndexProvider(
                new IndexLayout(tempDir), AnalyzerFactory.japanese())) {
            p.refreshAnalyzer("brand-new");
            assertThat(p.isOpen("brand-new")).isTrue();
        }
    }

    @Test
    void filesystemRemoveActuallyDeletesDirectory() throws Exception {
        final IndexLayout layout = new IndexLayout(tempDir);
        try (LuceneIndexProvider p = new LuceneIndexProvider(layout,
                AnalyzerFactory.japanese())) {
            p.getOrCreate("rm-ns");
            assertThat(Files.exists(layout.directoryFor("rm-ns"))).isTrue();
            p.remove("rm-ns", true);
            assertThat(Files.exists(layout.directoryFor("rm-ns"))).isFalse();
        }
    }
}
