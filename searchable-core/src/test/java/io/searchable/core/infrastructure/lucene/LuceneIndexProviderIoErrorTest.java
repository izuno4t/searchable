package io.searchable.core.infrastructure.lucene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Exercises the remaining IOException catch blocks in
 * {@link LuceneIndexProvider} via mocked static {@link Files}.
 */
class LuceneIndexProviderIoErrorTest {

    @TempDir Path tempDir;

    @Test
    void isEmptyDirReturnsTrueWhenFilesListThrows() throws Exception {
        // Seed the namespace dir so the !isDirectory short-circuit doesn't
        // fire; then mock Files.list to throw -> isEmptyDir returns true
        // -> openReadOnly raises NoSuchElementException.
        final IndexLayout layout = new IndexLayout(tempDir);
        Files.createDirectories(layout.directoryFor("broken-list"));
        try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             LuceneIndexProvider p = new LuceneIndexProvider(layout,
                 AnalyzerFactory.japanese(), true)) {
            files.when(() -> Files.list(any(Path.class)))
                .thenThrow(new IOException("list-boom"));
            assertThatThrownBy(() -> p.getOrCreate("broken-list"))
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void closeSwallowsContextCloseIoException() throws Exception {
        // Build a real provider and inject a context that throws on close
        // so the close() iteration's catch block fires.
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("idx")), AnalyzerFactory.japanese())) {
            // Acquire a context, then replace it via reflection with a
            // mocked one that throws on close.
            provider.getOrCreate("ns");
            final var contextsField = LuceneIndexProvider.class.getDeclaredField("contexts");
            contextsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final java.util.Map<String, LuceneIndexContext> contexts =
                (java.util.Map<String, LuceneIndexContext>) contextsField.get(provider);

            final LuceneIndexContext broken = mock(LuceneIndexContext.class);
            doThrow(new IOException("close-boom")).when(broken).close();
            contexts.put("ns", broken);
            // provider.close() will iterate and hit the catch block.
        }
    }
}
