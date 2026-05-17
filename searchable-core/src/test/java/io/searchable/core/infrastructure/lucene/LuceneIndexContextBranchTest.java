package io.searchable.core.infrastructure.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Branch coverage for {@link LuceneIndexContext}. */
class LuceneIndexContextBranchTest {

    @TempDir Path tempDir;

    private LuceneIndexContext newReadWrite() throws IOException {
        final Directory dir = new ByteBuffersDirectory();
        final Analyzer analyzer = new StandardAnalyzer();
        final IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
        writer.commit();
        final SearcherManager sm = new SearcherManager(writer, null);
        return new LuceneIndexContext("ns", dir, analyzer, writer, sm);
    }

    private LuceneIndexContext newReadOnly() throws IOException {
        // Seed the directory by writing once, then open a DirectoryReader.
        final Directory dir = new ByteBuffersDirectory();
        final Analyzer analyzer = new StandardAnalyzer();
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
            w.commit();
        }
        final DirectoryReader reader = DirectoryReader.open(dir);
        final SearcherManager sm = new SearcherManager(reader, null);
        return new LuceneIndexContext("ns", dir, analyzer, sm);
    }

    @Test
    void writerThrowsInReadOnlyContext() throws Exception {
        try (LuceneIndexContext ctx = newReadOnly()) {
            assertThat(ctx.isReadOnly()).isTrue();
            assertThatThrownBy(ctx::writer)
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void readOnlyRefreshReopensReader() throws Exception {
        try (LuceneIndexContext ctx = newReadOnly()) {
            ctx.refresh(); // exercises the readOnly branch in refresh()
        }
    }

    @Test
    void readWriteRefreshAndAccessors() throws Exception {
        try (LuceneIndexContext ctx = newReadWrite()) {
            assertThat(ctx.namespaceId()).isEqualTo("ns");
            assertThat(ctx.analyzer()).isNotNull();
            assertThat(ctx.isReadOnly()).isFalse();
            assertThat(ctx.writer()).isNotNull();
            ctx.refresh();
            assertThat(ctx.documentCount()).isZero();
            assertThat(ctx.indexSizeBytes()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    void closeAggregatesSearcherManagerError() throws Exception {
        final Directory dir = new ByteBuffersDirectory();
        final Analyzer analyzer = new StandardAnalyzer();
        final IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
        writer.commit();
        // SearcherManager.close() can be made to throw via mock.
        final SearcherManager sm = mock(SearcherManager.class);
        doThrow(new IOException("sm-close")).when(sm).close();

        final LuceneIndexContext ctx = new LuceneIndexContext("ns", dir, analyzer, writer, sm);
        assertThatThrownBy(ctx::close)
            .isInstanceOf(IOException.class)
            .hasMessage("sm-close");
    }

    @Test
    void closeAggregatesWriterErrorWhenSearcherManagerOk() throws Exception {
        final Directory dir = new ByteBuffersDirectory();
        final Analyzer analyzer = new StandardAnalyzer();
        final IndexWriter writer = mock(IndexWriter.class);
        doThrow(new IOException("writer-close")).when(writer).close();
        final SearcherManager sm = mock(SearcherManager.class);

        final LuceneIndexContext ctx = new LuceneIndexContext("ns", dir, analyzer, writer, sm);
        assertThatThrownBy(ctx::close)
            .isInstanceOf(IOException.class)
            .hasMessage("writer-close");
    }

    @Test
    void closeAggregatesDirectoryErrorWhenSmAndWriterOk() throws Exception {
        final Directory dir = mock(Directory.class);
        doThrow(new IOException("dir-close")).when(dir).close();
        final Analyzer analyzer = new StandardAnalyzer();
        final IndexWriter writer = mock(IndexWriter.class);
        final SearcherManager sm = mock(SearcherManager.class);

        final LuceneIndexContext ctx = new LuceneIndexContext("ns", dir, analyzer, writer, sm);
        assertThatThrownBy(ctx::close)
            .isInstanceOf(IOException.class)
            .hasMessage("dir-close");
    }

    @Test
    void closeSwallowsAnalyzerException() throws Exception {
        // Analyzer.close() declares Exception but is typically a no-op; we
        // simulate one that throws and confirm close still completes when
        // every other resource closed cleanly.
        final Directory dir = mock(Directory.class);
        final Analyzer analyzer = mock(Analyzer.class);
        doThrow(new RuntimeException("analyzer-boom")).when(analyzer).close();
        final IndexWriter writer = mock(IndexWriter.class);
        final SearcherManager sm = mock(SearcherManager.class);

        final LuceneIndexContext ctx = new LuceneIndexContext("ns", dir, analyzer, writer, sm);
        ctx.close(); // must not throw because analyzer exception is swallowed
    }

    @Test
    void readOnlyContextCloseSkipsWriterPath() throws Exception {
        // When writer is null (read-only ctor) close still completes.
        try (LuceneIndexContext ctx = newReadOnly()) {
            // construct + close exercises the writer == null branch
        }
    }

    @Test
    void indexSizeBytesSkipsConcurrentlyDeletedFile() throws Exception {
        // directory.fileLength throws IOException for a missing file ->
        // the loop swallows it and continues.
        final Directory dir = mock(Directory.class);
        when(dir.listAll()).thenReturn(new String[]{"a", "missing"});
        when(dir.fileLength("a")).thenReturn(100L);
        when(dir.fileLength("missing")).thenThrow(new IOException("gone"));
        final Analyzer analyzer = new StandardAnalyzer();
        final IndexWriter writer = mock(IndexWriter.class);
        final SearcherManager sm = mock(SearcherManager.class);

        final LuceneIndexContext ctx = new LuceneIndexContext("ns", dir, analyzer, writer, sm);
        assertThat(ctx.indexSizeBytes()).isEqualTo(100L);
    }

    @Test
    void unusedHelperReturnsSearcher() throws Exception {
        try (LuceneIndexContext ctx = newReadOnly()) {
            // ctx.acquireSearcher is the prod API; unused() is a static helper
            // retained for binary compat.
            final var searcher = ctx.acquireSearcher();
            assertThat(searcher).isNotNull();
            ctx.release(searcher);
        }
    }
}
