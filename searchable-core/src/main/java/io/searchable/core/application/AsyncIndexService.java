package io.searchable.core.application;

import io.searchable.core.domain.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps {@link IndexService} so callers can queue ingest work and observe
 * its progress without blocking.
 *
 * <p>Each namespace receives its own single-threaded executor. This
 * preserves Lucene's per-namespace serialization guarantee
 * (one writer per directory) while letting different namespaces run in
 * parallel — the same isolation rule documented in
 * {@code docs/devel/design/architecture/overview.md} §7.1.
 *
 * <p>The implementation is suitable for moderate throughput. For very
 * high ingest volumes, callers should batch documents via
 * {@link IndexService#indexBatch(String, java.util.List)} instead.
 */
public final class AsyncIndexService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncIndexService.class);

    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private final IndexService delegate;
    private final Duration shutdownTimeout;
    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> queueDepth = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> processed = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failed = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public AsyncIndexService(final IndexService delegate) {
        this(delegate, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Visible for tests so they can verify the "timeout exceeded" branch
     * of {@link #close()} without waiting the production-default 30 s.
     */
    AsyncIndexService(final IndexService delegate, final Duration shutdownTimeout) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout,
            "shutdownTimeout must not be null");
    }

    /**
     * Enqueue a single document for asynchronous indexing.
     *
     * @return a future that completes when the underlying
     *         {@link IndexService#index(Document)} call finishes
     * @throws IllegalStateException if {@link #close()} has already been called
     */
    public CompletableFuture<Void> submit(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        if (closed) {
            throw new IllegalStateException("AsyncIndexService is closed");
        }
        final String namespaceId = document.namespaceId();
        final ExecutorService executor = executors.computeIfAbsent(namespaceId,
            id -> Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r, "searchable-async-index-" + id);
                t.setDaemon(true);
                return t;
            }));
        queueDepth(namespaceId).incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                delegate.index(document);
                processed(namespaceId).incrementAndGet();
            } catch (RuntimeException e) {
                failed(namespaceId).incrementAndGet();
                log.error("async index failed for {}/{}: {}",
                    namespaceId, document.id(), e.getMessage(), e);
                throw e;
            } finally {
                queueDepth(namespaceId).decrementAndGet();
            }
        }, executor);
    }

    /** Snapshot of queue depth, processed count, and failure count per namespace. */
    public Map<String, NamespaceStats> stats() {
        return executors.keySet().stream().collect(java.util.stream.Collectors.toMap(
            ns -> ns,
            ns -> new NamespaceStats(queueDepth(ns).get(), processed(ns).get(), failed(ns).get())));
    }

    private AtomicLong queueDepth(final String ns) {
        return queueDepth.computeIfAbsent(ns, k -> new AtomicLong());
    }

    private AtomicLong processed(final String ns) {
        return processed.computeIfAbsent(ns, k -> new AtomicLong());
    }

    private AtomicLong failed(final String ns) {
        return failed.computeIfAbsent(ns, k -> new AtomicLong());
    }

    @Override
    public void close() {
        closed = true;
        executors.forEach((id, exec) -> {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("async indexer for {} did not terminate within {}; forcing",
                        id, shutdownTimeout);
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exec.shutdownNow();
            }
        });
        executors.clear();
    }

    /**
     * Per-namespace queue statistics.
     *
     * @param queued      documents currently waiting in or executing on the queue
     * @param processed   total documents successfully indexed since start-up
     * @param failed      total documents whose ingest threw
     */
    public record NamespaceStats(long queued, long processed, long failed) { }
}
