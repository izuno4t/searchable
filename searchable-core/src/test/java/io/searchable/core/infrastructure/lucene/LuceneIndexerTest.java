package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuceneIndexerTest {

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

    private Document doc(final String id, final String title, final String content) {
        return Document.builder()
            .id(id).namespaceId("ns-1")
            .title(title).content(content)
            .metadata(Map.of("source", "test"))
            .build();
    }

    @Test
    void indexSingleDocumentIsVisibleAfterRefresh() throws Exception {
        indexer.index(doc("d1", "タイトル", "本文の検索テスト"));
        assertThat(provider.getOrCreate("ns-1").documentCount()).isEqualTo(1L);
    }

    @Test
    void indexBatchInsertsAll() throws Exception {
        indexer.indexBatch("ns-1", List.of(
            doc("d1", "t1", "c1"),
            doc("d2", "t2", "c2"),
            doc("d3", "t3", "c3")
        ));
        assertThat(provider.getOrCreate("ns-1").documentCount()).isEqualTo(3L);
    }

    @Test
    void indexUpdatesExistingDocumentInPlace() throws Exception {
        indexer.index(doc("d1", "old-title", "old-content"));
        indexer.index(doc("d1", "new-title", "new-content"));
        assertThat(provider.getOrCreate("ns-1").documentCount()).isEqualTo(1L);
    }

    @Test
    void deleteRemovesDocument() throws Exception {
        indexer.index(doc("d1", "t", "c"));
        assertThat(indexer.delete("ns-1", "d1")).isTrue();
        assertThat(provider.getOrCreate("ns-1").documentCount()).isZero();
    }

    @Test
    void deleteAllClearsNamespace() throws Exception {
        indexer.indexBatch("ns-1", List.of(doc("d1", "t", "c"), doc("d2", "t", "c")));
        indexer.deleteAll("ns-1");
        assertThat(provider.getOrCreate("ns-1").documentCount()).isZero();
    }

    @Test
    void mismatchedNamespaceIsRejected() {
        final Document foreign = Document.builder()
            .id("d1").namespaceId("other").title("t").content("c").build();
        assertThatThrownBy(() -> indexer.indexBatch("ns-1", List.of(foreign)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
