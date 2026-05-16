package io.searchable.core.infrastructure.lucene;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the lifecycle of {@link LuceneIndexContext} instances, one per namespace.
 *
 * <p>Use {@link #getOrCreate(String)} to lazily open an index, and
 * {@link #close()} (or {@link #remove(String, boolean)}) when the namespace is
 * dropped.
 *
 * <p>In <strong>read-only mode</strong> the provider opens a
 * {@link DirectoryReader} for each namespace instead of an
 * {@link IndexWriter}. Requesting a namespace that does not yet have an
 * index directory on disk throws {@link NoSuchElementException} (the
 * provider will never create one in read-only mode).
 */
public final class LuceneIndexProvider implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexProvider.class);

    private final IndexLayout layout;
    private final AnalyzerFactory analyzerFactory;
    private final boolean readOnly;
    private final Map<String, LuceneIndexContext> contexts = new ConcurrentHashMap<>();

    public LuceneIndexProvider(final IndexLayout layout, final AnalyzerFactory analyzerFactory) {
        this(layout, analyzerFactory, false);
    }

    public LuceneIndexProvider(final IndexLayout layout,
                               final AnalyzerFactory analyzerFactory,
                               final boolean readOnly) {
        this.layout = Objects.requireNonNull(layout, "layout must not be null");
        this.analyzerFactory = Objects.requireNonNull(analyzerFactory, "analyzerFactory must not be null");
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** Open (and cache) the index for a namespace, creating it on disk if needed. */
    public LuceneIndexContext getOrCreate(final String namespaceId) {
        return contexts.computeIfAbsent(namespaceId, this::openContext);
    }

    /** Return whether an index context is currently open. */
    public boolean isOpen(final String namespaceId) {
        return contexts.containsKey(namespaceId);
    }

    /**
     * Reopen the index context so that a refreshed
     * {@link AnalyzerFactory} (e.g. after a user-dictionary update) takes
     * effect immediately. Existing on-disk segments are kept; only the
     * in-memory writer/reader state is rebuilt.
     */
    public void refreshAnalyzer(final String namespaceId) throws IOException {
        final LuceneIndexContext ctx = contexts.remove(namespaceId);
        if (ctx != null) {
            ctx.close();
        }
        getOrCreate(namespaceId);
    }

    /** Close and remove the context, optionally wiping the index files. */
    public void remove(final String namespaceId, final boolean deleteFiles) throws IOException {
        final LuceneIndexContext ctx = contexts.remove(namespaceId);
        if (ctx != null) {
            ctx.close();
        }
        if (deleteFiles) {
            if (readOnly) {
                throw new IllegalStateException(
                    "Cannot delete index files in read-only mode (namespace=" + namespaceId + ")");
            }
            final Path dir = layout.directoryFor(namespaceId);
            if (Files.exists(dir)) {
                FileUtils.deleteDirectory(dir.toFile());
            }
        }
    }

    @Override
    public void close() {
        contexts.forEach((id, ctx) -> {
            try {
                ctx.close();
            } catch (IOException e) {
                log.warn("Failed to close index for namespace {}", id, e);
            }
        });
        contexts.clear();
    }

    private LuceneIndexContext openContext(final String namespaceId) {
        final Path path = layout.directoryFor(namespaceId);
        return readOnly ? openReadOnly(namespaceId, path) : openReadWrite(namespaceId, path);
    }

    private LuceneIndexContext openReadWrite(final String namespaceId, final Path path) {
        try {
            Files.createDirectories(path);
            final Directory directory = new MMapDirectory(path);
            final Analyzer analyzer = analyzerFactory.create(namespaceId);
            final IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setRAMBufferSizeMB(64);
            final IndexWriter writer = new IndexWriter(directory, config);
            // Initial commit so the SearcherManager has a readable index.
            writer.commit();
            final SearcherManager manager = new SearcherManager(writer, true, true, null);
            log.info("opened Lucene index for namespace {} at {}", namespaceId, path);
            return new LuceneIndexContext(namespaceId, directory, analyzer, writer, manager);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to open Lucene index for namespace " + namespaceId, e);
        }
    }

    private LuceneIndexContext openReadOnly(final String namespaceId, final Path path) {
        if (!Files.isDirectory(path) || isEmptyDir(path)) {
            throw new NoSuchElementException(
                "No index found for namespace " + namespaceId
                    + " (path=" + path + ") in read-only mode");
        }
        try {
            final Directory directory = new MMapDirectory(path);
            final Analyzer analyzer = analyzerFactory.create(namespaceId);
            final DirectoryReader reader = DirectoryReader.open(directory);
            final SearcherManager manager = new SearcherManager(reader, null);
            log.info("opened read-only Lucene index for namespace {} at {}", namespaceId, path);
            return new LuceneIndexContext(namespaceId, directory, analyzer, manager);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to open Lucene index for namespace " + namespaceId, e);
        }
    }

    private static boolean isEmptyDir(final Path path) {
        try (var stream = Files.list(path)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }
}
