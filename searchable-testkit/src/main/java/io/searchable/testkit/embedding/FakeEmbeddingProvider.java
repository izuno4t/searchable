package io.searchable.testkit.embedding;

import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;

/**
 * Deterministic embedding provider for tests.
 *
 * <p>Backed by {@link HashEmbeddingProvider} (no ONNX Runtime load).
 * Produces stable vectors that are sufficient for similarity ordering
 * assertions in unit and integration tests.
 *
 * @see HashEmbeddingProvider
 */
public final class FakeEmbeddingProvider implements EmbeddingProvider {

    public static final int DEFAULT_DIMENSION = 128;

    private final HashEmbeddingProvider delegate;

    public FakeEmbeddingProvider() {
        this(DEFAULT_DIMENSION);
    }

    public FakeEmbeddingProvider(final int dimension) {
        this.delegate = new HashEmbeddingProvider(dimension);
    }

    @Override public float[] embed(final String text) { return delegate.embed(text); }
    @Override public int dimension() { return delegate.dimension(); }
    @Override public String identifier() { return "fake:" + delegate.identifier(); }
    @Override public void close() { delegate.close(); }
}
