package io.searchable.core.infrastructure.lucene;

import org.apache.lucene.store.LockObtainFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that Lucene's per-directory {@code write.lock} prevents concurrent
 * write access within the same namespace, while letting different namespaces
 * be written to in parallel.
 *
 * <p>Multi-JVM verification is out of scope for unit tests (see the task
 * note for TASK-074); this test covers the single-JVM case which captures
 * the same locking semantics.
 */
class LuceneNamespaceLockTest {

    @TempDir Path tempDir;

    @Test
    void secondWriterOnSameNamespaceFailsWithLockObtainFailed() {
        final LuceneIndexProvider first = new LuceneIndexProvider(
            new IndexLayout(tempDir), AnalyzerFactory.japanese());
        first.getOrCreate("ns_locked"); // acquires write.lock

        final LuceneIndexProvider second = new LuceneIndexProvider(
            new IndexLayout(tempDir), AnalyzerFactory.japanese());
        try {
            assertThatThrownBy(() -> second.getOrCreate("ns_locked"))
                .hasRootCauseInstanceOf(LockObtainFailedException.class);
        } finally {
            try { first.close(); } catch (Exception ignored) { }
            try { second.close(); } catch (Exception ignored) { }
        }
    }

    @Test
    void differentNamespacesCanBeOpenedInParallel() throws Exception {
        final LuceneIndexProvider holder = new LuceneIndexProvider(
            new IndexLayout(tempDir), AnalyzerFactory.japanese());
        holder.getOrCreate("ns_a");

        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final Thread t = new Thread(() -> {
            final LuceneIndexProvider other = new LuceneIndexProvider(
                new IndexLayout(tempDir), AnalyzerFactory.japanese());
            try {
                other.getOrCreate("ns_b");
                ready.countDown();
            } catch (Throwable e) {
                failure.set(e);
                ready.countDown();
            } finally {
                try { other.close(); } catch (Exception ignored) { }
            }
        }, "ns_b-writer");
        t.start();
        ready.await();
        t.join();

        try {
            assertThat(failure.get()).isNull();
        } finally {
            holder.close();
        }
    }
}
