package com.searchable.core.infrastructure.embedding;

import com.searchable.core.domain.embedding.EmbeddingProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Deterministic, model-free embedding provider used for tests and as a
 * fallback when no ONNX model is configured.
 *
 * <p>Produces unit-length vectors by hashing the input text with SHA-256
 * and folding the bytes into the target dimension. The output is
 * reproducible but carries no semantic meaning; do not use for real
 * relevance.
 */
public final class HashEmbeddingProvider implements EmbeddingProvider {

    private final int dimension;

    public HashEmbeddingProvider(final int dimension) {
        if (dimension <= 0 || dimension % 8 != 0) {
            throw new IllegalArgumentException(
                "dimension must be positive and a multiple of 8, was " + dimension);
        }
        this.dimension = dimension;
    }

    @Override
    public float[] embed(final String text) {
        Objects.requireNonNull(text, "text must not be null");
        final byte[] base = sha256(text.getBytes(StandardCharsets.UTF_8));
        final float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            final byte b = base[i % base.length];
            // Map signed byte to a centered float in [-1, 1).
            vector[i] = (b & 0xFF) / 128.0f - 1.0f;
        }
        return normalize(vector);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String identifier() {
        return "hash:" + dimension;
    }

    private static float[] normalize(final float[] vector) {
        double sum = 0.0;
        for (final float v : vector) {
            sum += (double) v * v;
        }
        final double norm = Math.sqrt(sum);
        if (norm == 0.0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    private static byte[] sha256(final byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
