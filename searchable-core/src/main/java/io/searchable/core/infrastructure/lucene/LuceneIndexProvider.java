package io.searchable.core.infrastructure.lucene;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.ByteBuffersDirectory;
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
    private final StorageBackend backend;
    private final Map<String, LuceneIndexContext> contexts = new ConcurrentHashMap<>();

    public LuceneIndexProvider(final IndexLayout layout, final AnalyzerFactory analyzerFactory) {
        this(layout, analyzerFactory, false, StorageBackend.FILESYSTEM);
    }

    public LuceneIndexProvider(final IndexLayout layout,
                               final AnalyzerFactory analyzerFactory,
                               final boolean readOnly) {
        this(layout, analyzerFactory, readOnly, StorageBackend.FILESYSTEM);
    }

    public LuceneIndexProvider(final IndexLayout layout,
                               final AnalyzerFactory analyzerFactory,
                               final boolean readOnly,
                               final StorageBackend backend) {
        this.layout = Objects.requireNonNull(layout, "layout must not be null");
        this.analyzerFactory = Objects.requireNonNull(analyzerFactory, "analyzerFactory must not be null");
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        if (backend == StorageBackend.MEMORY && readOnly) {
            throw new IllegalArgumentException(
                "MEMORY storage backend cannot be combined with read-only mode");
        }
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public StorageBackend backend() {
        return backend;
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
            if (backend == StorageBackend.FILESYSTEM) {
                final Path dir = layout.directoryFor(namespaceId);
                if (Files.exists(dir)) {
                    FileUtils.deleteDirectory(dir.toFile());
                }
            }
            // MEMORY backend: closing the context already discards the buffers.
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
        if (readOnly) {
            // MEMORY + readOnly is rejected at construction time, so this is FILESYSTEM.
            return openReadOnly(namespaceId, layout.directoryFor(namespaceId));
        }
        return switch (backend) {
            case FILESYSTEM -> openFilesystemReadWrite(namespaceId, layout.directoryFor(namespaceId));
            case MEMORY -> openMemoryReadWrite(namespaceId);
        };
    }

    private LuceneIndexContext openFilesystemReadWrite(final String namespaceId, final Path path) {
        try {
            Files.createDirectories(path);
            final Directory directory = new MMapDirectory(path);
            return openWriter(namespaceId, directory, "at " + path);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to open Lucene index for namespace " + namespaceId, e);
        }
    }

    private LuceneIndexContext openMemoryReadWrite(final String namespaceId) {
        try {
            final Directory directory = new ByteBuffersDirectory();
            return openWriter(namespaceId, directory, "in memory");
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to open in-memory Lucene index for namespace " + namespaceId, e);
        }
    }

    private LuceneIndexContext openWriter(final String namespaceId,
                                          final Directory directory,
                                          final String locationLabel) throws IOException {
        final Analyzer analyzer = analyzerFactory.create(namespaceId);
        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setRAMBufferSizeMB(64);
        final IndexWriter writer = new IndexWriter(directory, config);
        // Initial commit so the SearcherManager has a readable index.
        writer.commit();
        final SearcherManager manager = new SearcherManager(writer, true, true, null);
        log.info("opened Lucene index for namespace {} {}", namespaceId, locationLabel);
        return new LuceneIndexContext(namespaceId, directory, analyzer, writer, manager);
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
