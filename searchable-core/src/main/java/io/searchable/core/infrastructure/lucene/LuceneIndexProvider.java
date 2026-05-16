package io.searchable.core.infrastructure.lucene;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the lifecycle of {@link LuceneIndexContext} instances, one per namespace.
 *
 * <p>Use {@link #getOrCreate(String)} to lazily open an index, and
 * {@link #close()} (or {@link #remove(String)}) when the namespace is dropped.
 */
public final class LuceneIndexProvider implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexProvider.class);

    private final IndexLayout layout;
    private final AnalyzerFactory analyzerFactory;
    private final Map<String, LuceneIndexContext> contexts = new ConcurrentHashMap<>();

    public LuceneIndexProvider(final IndexLayout layout, final AnalyzerFactory analyzerFactory) {
        this.layout = Objects.requireNonNull(layout, "layout must not be null");
        this.analyzerFactory = Objects.requireNonNull(analyzerFactory, "analyzerFactory must not be null");
    }

    /** Open (and cache) the index for a namespace, creating it on disk if needed. */
    public LuceneIndexContext getOrCreate(final String namespaceId) {
        return contexts.computeIfAbsent(namespaceId, this::openContext);
    }

    /** Return whether an index context is currently open. */
    public boolean isOpen(final String namespaceId) {
        return contexts.containsKey(namespaceId);
    }

    /** Close and remove the context, optionally wiping the index files. */
    public void remove(final String namespaceId, final boolean deleteFiles) throws IOException {
        final LuceneIndexContext ctx = contexts.remove(namespaceId);
        if (ctx != null) {
            ctx.close();
        }
        if (deleteFiles) {
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
}
