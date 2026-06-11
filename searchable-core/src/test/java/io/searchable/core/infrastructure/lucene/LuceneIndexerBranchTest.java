package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LuceneIndexerBranchTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private LuceneIndexer indexer;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(new IndexLayout(tempDir), AnalyzerFactory.japanese());
        indexer = new LuceneIndexer(provider);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private Document doc(final String ns, final String id) {
        return Document.builder().id(id).namespaceId(ns).title("t").content("c").build();
    }

    @Test
    void constructorsRejectNullArgs() {
        assertThatThrownBy(() -> new LuceneIndexer(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LuceneIndexer(provider, null, null,
            new io.searchable.core.infrastructure.chunking.WholeDocumentChunkingStrategy()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LuceneIndexer(provider, new LuceneDocumentMapper(), null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteShortCircuitsWhenNamespaceUnopened() {
        // isOpen=false branch: return false without touching writer.
        assertThat(indexer.delete("never-opened", "doc")).isFalse();
    }

    @Test
    void deleteReturnsFalseWhenDocumentMissing() {
        indexer.index(doc("ns", "exists"));
        assertThat(indexer.delete("ns", "missing")).isFalse();
    }

    @Test
    void deleteReturnsTrueWhenDocumentRemoved() {
        indexer.index(doc("ns", "target"));
        assertThat(indexer.delete("ns", "target")).isTrue();
    }

    @Test
    void indexBatchRejectsDocFromAnotherNamespace() {
        assertThatThrownBy(() -> indexer.indexBatch("ns-a", List.of(doc("ns-b", "x"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteAllClearsNamespace() {
        indexer.index(doc("ns", "d1"));
        indexer.index(doc("ns", "d2"));
        indexer.deleteAll("ns");
        assertThat(indexer.delete("ns", "d1")).isFalse();
    }

    @Test
    void indexWrapsWriterIoException() throws Exception {
        // Mock provider + context + writer so writer.commit() throws.
        final LuceneIndexProvider mockedProvider = mock(LuceneIndexProvider.class);
        final LuceneIndexContext mockedCtx = mock(LuceneIndexContext.class);
        final IndexWriter writer = mock(IndexWriter.class);
        when(mockedProvider.getOrCreate(any())).thenReturn(mockedCtx);
        when(mockedCtx.writer()).thenReturn(writer);
        doThrow(new IOException("commit-boom")).when(writer).commit();

        final LuceneIndexer i = new LuceneIndexer(mockedProvider);
        assertThatThrownBy(() -> i.index(doc("ns", "x")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to index document");
    }

    @Test
    void indexBatchWrapsWriterIoException() throws Exception {
        final LuceneIndexProvider mockedProvider = mock(LuceneIndexProvider.class);
        final LuceneIndexContext mockedCtx = mock(LuceneIndexContext.class);
        final IndexWriter writer = mock(IndexWriter.class);
        when(mockedProvider.getOrCreate(any())).thenReturn(mockedCtx);
        when(mockedCtx.writer()).thenReturn(writer);
        doThrow(new IOException("commit-batch-boom")).when(writer).commit();

        final LuceneIndexer i = new LuceneIndexer(mockedProvider);
        assertThatThrownBy(() -> i.indexBatch("ns", List.of(doc("ns", "x"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to index batch");
    }

    @Test
    void deleteWrapsWriterIoException() throws Exception {
        final LuceneIndexProvider mockedProvider = mock(LuceneIndexProvider.class);
        final LuceneIndexContext mockedCtx = mock(LuceneIndexContext.class);
        final IndexWriter writer = mock(IndexWriter.class);
        when(mockedProvider.isOpen(any())).thenReturn(true);
        when(mockedProvider.getOrCreate(any())).thenReturn(mockedCtx);
        when(mockedCtx.writer()).thenReturn(writer);
        when(mockedCtx.documentCount()).thenReturn(1L);
        doThrow(new IOException("delete-boom")).when(writer).commit();

        final LuceneIndexer i = new LuceneIndexer(mockedProvider);
        assertThatThrownBy(() -> i.delete("ns", "doc"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to delete document");
    }

    @Test
    void deleteAllWrapsWriterIoException() throws Exception {
        final LuceneIndexProvider mockedProvider = mock(LuceneIndexProvider.class);
        final LuceneIndexContext mockedCtx = mock(LuceneIndexContext.class);
        final IndexWriter writer = mock(IndexWriter.class);
        when(mockedProvider.getOrCreate(any())).thenReturn(mockedCtx);
        when(mockedCtx.writer()).thenReturn(writer);
        doThrow(new IOException("clear-boom")).when(writer).commit();

        final LuceneIndexer i = new LuceneIndexer(mockedProvider);
        assertThatThrownBy(() -> i.deleteAll("ns"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to clear namespace");
    }

    @Test
    void rebuildRejectsDocumentBelongingToDifferentNamespace() {
        // The mismatch check inside the rebuild loop throws an
        // IllegalArgumentException; covers L138 of LuceneIndexer.
        provider.getOrCreate("real-ns");
        final Document foreign = doc("OTHER", "x");
        assertThatThrownBy(() -> indexer.rebuild("real-ns", List.of(foreign)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to namespace");
    }

    @Test
    void apiRejectsNullArgs() {
        assertThatThrownBy(() -> indexer.index(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> indexer.indexBatch(null, List.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> indexer.indexBatch("ns", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> indexer.delete(null, "doc"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> indexer.delete("ns", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> indexer.deleteAll(null))
            .isInstanceOf(NullPointerException.class);
    }
}
