package io.searchable.core.infrastructure.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Holds the per-namespace Lucene resources: {@link Directory},
 * {@link IndexWriter}, and {@link SearcherManager}.
 *
 * <p>Must be closed via {@link #close()} when the namespace is no longer
 * active (e.g. on shutdown or namespace deletion).
 *
 * <p>In read-only mode the context owns a {@link DirectoryReader} rather
 * than an {@link IndexWriter}; calls to {@link #writer()} or {@link #refresh()}
 * fail with {@link IllegalStateException}.
 */
public final class LuceneIndexContext implements AutoCloseable {

    private final String namespaceId;
    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;
    private final boolean readOnly;

    LuceneIndexContext(final String namespaceId,
                       final Directory directory,
                       final Analyzer analyzer,
                       final IndexWriter writer,
                       final SearcherManager searcherManager) {
        this.namespaceId = Objects.requireNonNull(namespaceId);
        this.directory = Objects.requireNonNull(directory);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.writer = Objects.requireNonNull(writer);
        this.searcherManager = Objects.requireNonNull(searcherManager);
        this.readOnly = false;
    }

    LuceneIndexContext(final String namespaceId,
                       final Directory directory,
                       final Analyzer analyzer,
                       final SearcherManager searcherManager) {
        this.namespaceId = Objects.requireNonNull(namespaceId);
        this.directory = Objects.requireNonNull(directory);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.writer = null;
        this.searcherManager = Objects.requireNonNull(searcherManager);
        this.readOnly = true;
    }

    public String namespaceId() { return namespaceId; }
    public Analyzer analyzer() { return analyzer; }

    public boolean isReadOnly() { return readOnly; }

    /**
     * The on-disk version directory backing this context, when the
     * underlying {@link Directory} is filesystem-based. Memory-backed
     * directories (e.g. {@link org.apache.lucene.store.ByteBuffersDirectory})
     * return {@link Optional#empty()}.
     *
     * <p>Used by {@link LuceneIndexProvider#refresh(String)} to detect
     * when a rebuild has promoted a new {@code <ts>/} so the read-only
     * context can be reopened against it.
     */
    public Optional<Path> versionDir() {
        if (directory instanceof FSDirectory fs) {
            return Optional.of(fs.getDirectory());
        }
        return Optional.empty();
    }

    public IndexWriter writer() {
        if (readOnly) {
            throw new IllegalStateException(
                "Index for namespace " + namespaceId + " is read-only");
        }
        return writer;
    }

    /** Refresh the searcher view to reflect recent writer commits. */
    public void refresh() throws IOException {
        if (readOnly) {
            // For DirectoryReader-backed searchers, maybeRefresh re-opens
            // the reader if the on-disk index has changed.
            searcherManager.maybeRefresh();
            return;
        }
        searcherManager.maybeRefresh();
    }

    /** Acquire an {@link IndexSearcher}; the caller must call {@link #release(IndexSearcher)}. */
    public IndexSearcher acquireSearcher() throws IOException {
        return searcherManager.acquire();
    }

    public void release(final IndexSearcher searcher) throws IOException {
        searcherManager.release(searcher);
    }

    /** Total size of index segments on disk in bytes. */
    public long indexSizeBytes() throws IOException {
        long total = 0L;
        for (final String name : directory.listAll()) {
            try {
                total += directory.fileLength(name);
            } catch (IOException ignore) {
                // file may have been deleted between listing and length lookup
            }
        }
        return total;
    }

    /** Document count according to the latest reader view. */
    public long documentCount() throws IOException {
        final IndexSearcher searcher = searcherManager.acquire();
        try {
            return searcher.getIndexReader().numDocs();
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public void close() throws IOException {
        IOException firstError = null;
        try {
            searcherManager.close();
        } catch (IOException e) {
            firstError = e;
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        try {
            directory.close();
        } catch (IOException e) {
            if (firstError == null) {
                firstError = e;
            }
        }
        try {
            analyzer.close();
        } catch (Exception ignored) {
            // Analyzer.close throws Exception; ignore
        }
        if (firstError != null) {
            throw firstError;
        }
    }

    static IndexSearcher unused(final DirectoryReader reader) {
        return new IndexSearcher(reader);
    }
}
