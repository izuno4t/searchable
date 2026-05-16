package io.searchable.core.application;

import io.searchable.core.domain.document.Document;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentBrowserTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private DocumentBrowser browser;
    private LuceneIndexer indexer;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(new IndexLayout(tempDir), AnalyzerFactory.japanese());
        indexer = new LuceneIndexer(provider);
        browser = new DocumentBrowser(provider);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private Document doc(final String id, final String title, final String content) {
        return Document.builder()
            .id(id).namespaceId("ns")
            .title(title).content(content)
            .indexedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
    }

    @Test
    void emptyIndexReturnsEmptyPage() {
        provider.getOrCreate("ns");
        final DocumentPage page = browser.list("ns", 0, 10);
        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    void listReturnsAllDocumentsBelowLimit() {
        indexer.indexBatch("ns", List.of(
            doc("d1", "t1", "本文1"),
            doc("d2", "t2", "本文2"),
            doc("d3", "t3", "本文3")
        ));
        final DocumentPage page = browser.list("ns", 0, 10);
        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.items()).extracting(DocumentSummary::id)
            .containsExactlyInAnyOrder("d1", "d2", "d3");
    }

    @Test
    void paginationReturnsRequestedSlice() {
        indexer.indexBatch("ns", List.of(
            doc("d1", "t1", "c1"),
            doc("d2", "t2", "c2"),
            doc("d3", "t3", "c3"),
            doc("d4", "t4", "c4")
        ));
        final DocumentPage page = browser.list("ns", 1, 2);
        assertThat(page.total()).isEqualTo(4L);
        assertThat(page.items()).hasSize(2);
    }

    @Test
    void snippetIsTruncated() {
        final String longContent = "あ".repeat(500);
        indexer.index(doc("d1", "t", longContent));
        final DocumentPage page = browser.list("ns", 0, 10);
        assertThat(page.items().get(0).snippet()).endsWith("...");
        assertThat(page.items().get(0).snippet().length()).isLessThan(longContent.length());
    }

    @Test
    void rejectsNegativeOffset() {
        assertThatThrownBy(() -> browser.list("ns", -1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
