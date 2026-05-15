package com.searchable.core.infrastructure.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.Objects;

/**
 * Holds the per-namespace Lucene resources: {@link Directory},
 * {@link IndexWriter}, and {@link SearcherManager}.
 *
 * <p>Must be closed via {@link #close()} when the namespace is no longer
 * active (e.g. on shutdown or namespace deletion).
 */
public final class LuceneIndexContext implements AutoCloseable {

    private final String namespaceId;
    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;

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
    }

    public String namespaceId() { return namespaceId; }
    public Analyzer analyzer() { return analyzer; }
    public IndexWriter writer() { return writer; }

    /** Refresh the searcher view to reflect recent writer commits. */
    public void refresh() throws IOException {
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
        try {
            writer.close();
        } catch (IOException e) {
            if (firstError == null) {
                firstError = e;
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
