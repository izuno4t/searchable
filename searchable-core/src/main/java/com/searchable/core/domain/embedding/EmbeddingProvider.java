package com.searchable.core.domain.embedding;

import java.util.List;

/**
 * Generates fixed-dimensional vector embeddings from text.
 *
 * <p>Implementations are stateless from the caller's perspective; the same
 * input must always produce the same vector. The dimension returned by
 * {@link #dimension()} must be constant for the lifetime of the provider so
 * that the Lucene HNSW index can store all vectors with the same shape.
 *
 * <p>Output vectors should be L2-normalized when the caller intends to use
 * them with cosine / inner-product similarity (the default for
 * {@code KnnFloatVectorField}).
 */
public interface EmbeddingProvider extends AutoCloseable {

    /** Encode a single text into an embedding vector. */
    float[] embed(String text);

    /** Encode a batch of texts. Default impl encodes one by one. */
    default List<float[]> embedAll(final List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /** Dimension of every vector produced by {@link #embed(String)}. */
    int dimension();

    /** Short identifier for diagnostics (e.g. {@code hash}, {@code onnx:multilingual-e5-small}). */
    String identifier();

    @Override
    default void close() { }
}
