package io.searchable.testkit.lucene;

import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;

import java.nio.file.Path;

/**
 * Lucene index fixture backed by a directory under {@code @TempDir}.
 *
 * <p>Wraps {@link LuceneIndexProvider} with sensible defaults
 * (Japanese analyzer) and bookkeeping for cleanup.
 */
public final class LuceneIndexFixture implements AutoCloseable {

    private final LuceneIndexProvider provider;
    private final IndexLayout layout;

    private LuceneIndexFixture(final LuceneIndexProvider provider, final IndexLayout layout) {
        this.provider = provider;
        this.layout = layout;
    }

    public static LuceneIndexFixture create(final Path baseDirectory) {
        return create(baseDirectory, AnalyzerFactory.japanese());
    }

    public static LuceneIndexFixture create(final Path baseDirectory,
                                            final AnalyzerFactory analyzerFactory) {
        final IndexLayout layout = new IndexLayout(baseDirectory);
        return new LuceneIndexFixture(new LuceneIndexProvider(layout, analyzerFactory), layout);
    }

    public LuceneIndexProvider provider() { return provider; }
    public IndexLayout layout() { return layout; }

    @Override
    public void close() {
        try {
            provider.close();
        } catch (Exception ignored) {
            // best-effort cleanup; @TempDir removes the files
        }
    }
}
