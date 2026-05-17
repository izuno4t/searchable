package io.searchable.core;

import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.application.config.IndexConfig;
import io.searchable.core.application.config.PluginsConfig;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SearchableLibraryCloseTest {

    @TempDir Path tempDir;

    @Test
    void closeSwallowsExceptionsFromIndividualCloseables() throws Exception {
        // Build the library with a mocked EmbeddingProvider whose close()
        // throws — the SearchableLibrary.close() loop's catch arm must
        // swallow the failure and continue closing the remaining resources.
        final EmbeddingProvider failing = mock(EmbeddingProvider.class);
        doThrow(new RuntimeException("close-boom")).when(failing).close();

        try (SearchableLibrary lib = SearchableLibrary.builder()
                .applicationConfig(new ApplicationConfig(
                    tempDir,
                    new PersistenceConfig("H2", "jdbc:h2:mem:lib-close-x;DB_CLOSE_DELAY=-1",
                        "sa", ""),
                    new IndexConfig(tempDir.resolve("idx")),
                    PluginsConfig.classpathOnly(),
                    GlobalConfig.defaults()))
                .embeddingProvider(failing)
                .build()) {
            // EmbeddingProvider passed via builder is NOT registered as a
            // closeable (only the default-created one is). Use reflection
            // to add it so close() iterates over the failing closeable.
            final var field = lib.getClass().getDeclaredField("closeables");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final java.util.Deque<AutoCloseable> closeables =
                (java.util.Deque<AutoCloseable>) field.get(lib);
            closeables.push(failing);
        }
        // try-with-resources fires close(); the mocked failing closer
        // throws but the loop catches and proceeds without re-throwing.
    }
}
