package io.searchable.core.infrastructure.lucene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuceneIndexProviderTest {

    @TempDir Path tempDir;

    @Test
    void getOrCreateOpensIndexLazilyAndReusesContext() throws Exception {
        final LuceneIndexProvider provider = new LuceneIndexProvider(
            new IndexLayout(tempDir), AnalyzerFactory.japanese());
        try {
            final LuceneIndexContext first = provider.getOrCreate("ns-1");
            final LuceneIndexContext second = provider.getOrCreate("ns-1");

            assertThat(first).isSameAs(second);
            assertThat(Files.exists(tempDir.resolve("ns-1"))).isTrue();
            assertThat(first.documentCount()).isZero();
            assertThat(first.indexSizeBytes()).isGreaterThanOrEqualTo(0L);
        } finally {
            provider.close();
        }
    }

    @Test
    void removeWithDeleteFilesWipesDirectory() throws Exception {
        final LuceneIndexProvider provider = new LuceneIndexProvider(
            new IndexLayout(tempDir), AnalyzerFactory.japanese());
        try {
            provider.getOrCreate("ns-1");
            assertThat(Files.exists(tempDir.resolve("ns-1"))).isTrue();

            provider.remove("ns-1", true);
            assertThat(provider.isOpen("ns-1")).isFalse();
            assertThat(Files.exists(tempDir.resolve("ns-1"))).isFalse();
        } finally {
            provider.close();
        }
    }

    @Test
    void layoutRejectsInvalidNamespaceId() {
        final IndexLayout layout = new IndexLayout(tempDir);
        assertThatThrownBy(() -> layout.directoryFor("Invalid/ID"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
