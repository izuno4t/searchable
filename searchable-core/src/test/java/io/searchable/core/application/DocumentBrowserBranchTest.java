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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Closes the validation/null branches in {@link DocumentBrowser}. */
class DocumentBrowserBranchTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private DocumentBrowser browser;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(new IndexLayout(tempDir), AnalyzerFactory.japanese());
        browser = new DocumentBrowser(provider);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    @Test
    void rejectsNullNamespace() {
        assertThatThrownBy(() -> browser.list(null, 0, 10))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> browser.list("ns", 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> browser.list("ns", 0, -3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void documentWithoutIndexedAtReturnsNullTimestamp() {
        final LuceneIndexer indexer = new LuceneIndexer(provider);
        indexer.index(Document.builder()
            .id("d").namespaceId("ns").title("title").content("body")
            .build());
        final DocumentPage page = browser.list("ns", 0, 10);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).indexedAt()).isNull();
    }
}
