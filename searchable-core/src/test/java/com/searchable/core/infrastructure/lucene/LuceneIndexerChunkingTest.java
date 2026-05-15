package com.searchable.core.infrastructure.lucene;

import com.searchable.core.domain.chunking.ChunkingStrategy;
import com.searchable.core.domain.document.Document;
import com.searchable.core.domain.embedding.EmbeddingProvider;
import com.searchable.core.domain.search.SearchRequest;
import com.searchable.core.domain.search.SearchResult;
import com.searchable.core.infrastructure.chunking.FixedSizeChunkingStrategy;
import com.searchable.core.infrastructure.chunking.WholeDocumentChunkingStrategy;
import com.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneIndexerChunkingTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private EmbeddingProvider embedding;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(new IndexLayout(tempDir), AnalyzerFactory.japanese());
        embedding = new HashEmbeddingProvider(128);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private Document doc(final String id, final String body) {
        return Document.builder()
            .id(id).namespaceId("ns").title("タイトル").content(body)
            .metadata(Map.of("format", "plain"))
            .build();
    }

    @Test
    void wholeStrategyProducesOneLuceneDocPerDocument() throws Exception {
        final LuceneIndexer indexer = new LuceneIndexer(provider, new LuceneDocumentMapper(),
            embedding, new WholeDocumentChunkingStrategy());
        indexer.index(doc("d1", "本文1"));
        indexer.index(doc("d2", "本文2"));
        assertThat(provider.getOrCreate("ns").documentCount()).isEqualTo(2L);
    }

    @Test
    void fixedStrategyExpandsLongDocumentIntoMultipleSubDocs() throws Exception {
        final ChunkingStrategy strategy = new FixedSizeChunkingStrategy(200, 40);
        final LuceneIndexer indexer = new LuceneIndexer(provider, new LuceneDocumentMapper(),
            embedding, strategy);
        indexer.index(doc("d1", "あ".repeat(1000)));
        // 1 document → multiple chunks → multiple Lucene sub-docs.
        assertThat(provider.getOrCreate("ns").documentCount()).isGreaterThan(1L);
    }

    @Test
    void reindexingReplacesAllChunksOfTheParent() throws Exception {
        final FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy(100, 10);
        final LuceneIndexer indexer = new LuceneIndexer(provider, new LuceneDocumentMapper(),
            embedding, strategy);
        indexer.index(doc("d1", "あ".repeat(600)));
        final long firstCount = provider.getOrCreate("ns").documentCount();
        assertThat(firstCount).isGreaterThan(1L);

        // Re-index with shorter content: chunk count drops, parent is replaced atomically.
        indexer.index(doc("d1", "短い"));
        final long secondCount = provider.getOrCreate("ns").documentCount();
        assertThat(secondCount).isLessThan(firstCount);
    }

    @Test
    void deletingParentRemovesAllChunks() throws Exception {
        final LuceneIndexer indexer = new LuceneIndexer(provider, new LuceneDocumentMapper(),
            embedding, new FixedSizeChunkingStrategy(100, 10));
        indexer.index(doc("d1", "あ".repeat(500)));
        assertThat(provider.getOrCreate("ns").documentCount()).isGreaterThan(1L);

        assertThat(indexer.delete("ns", "d1")).isTrue();
        assertThat(provider.getOrCreate("ns").documentCount()).isZero();
    }

    @Test
    void searchReturnsParentIdAsDocumentId() throws Exception {
        final LuceneIndexer indexer = new LuceneIndexer(provider, new LuceneDocumentMapper(),
            embedding, new FixedSizeChunkingStrategy(100, 10));
        indexer.index(doc("doc-7", "あ".repeat(300) + " 形態素解析 " + "あ".repeat(300)));

        final LuceneFullTextSearcher searcher = new LuceneFullTextSearcher(provider);
        final SearchResult result = searcher.search("ns",
            SearchRequest.builder().query("形態素解析").build());

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).documentId()).isEqualTo("doc-7");
    }
}
