package io.searchable.core.infrastructure.embedding;

import io.searchable.core.domain.embedding.Tokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the constructor validation paths of {@link OnnxEmbeddingProvider}.
 *
 * <p>The forward pass ({@code embed()}, {@code meanPool()}, {@code normalize()})
 * cannot be exercised without a real ONNX model binary on disk; bundling
 * {@code multilingual-e5-small.onnx} (≈470 MB) in the test resources is
 * impractical, so those paths remain uncovered. The class is documented as
 * a thin wrapper over the ONNX Runtime SPI for that reason.
 */
class OnnxEmbeddingProviderTest {

    @TempDir Path tempDir;

    private final Tokenizer dummyTokenizer = (text, max) ->
        new Tokenizer.Encoding(new long[max], new long[max]);

    @Test
    void rejectsNullIdentifier() {
        assertThatThrownBy(() -> new OnnxEmbeddingProvider(
                null, tempDir.resolve("x.onnx"), dummyTokenizer, 128, 64))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("identifier");
    }

    @Test
    void rejectsNullModelPath() {
        assertThatThrownBy(() -> new OnnxEmbeddingProvider(
                "m", null, dummyTokenizer, 128, 64))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("modelPath");
    }

    @Test
    void rejectsNullTokenizer() {
        assertThatThrownBy(() -> new OnnxEmbeddingProvider(
                "m", tempDir.resolve("x.onnx"), null, 128, 64))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tokenizer");
    }

    @Test
    void rejectsNonPositiveDimension() {
        assertThatThrownBy(() -> new OnnxEmbeddingProvider(
                "m", tempDir.resolve("x.onnx"), dummyTokenizer, 0, 64))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OnnxEmbeddingProvider(
                "m", tempDir.resolve("x.onnx"), dummyTokenizer, -5, 64))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaxSequenceLength() {
        assertThatThrownBy(() -> new OnnxEmbeddingProvider(
                "m", tempDir.resolve("x.onnx"), dummyTokenizer, 128, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrapsOrtFailureWhenModelFileMissing() {
        // OrtEnvironment will fail to load a path that does not exist.
        assertThatThrownBy(() -> new OnnxEmbeddingProvider(
                "missing-model", tempDir.resolve("does-not-exist.onnx"),
                dummyTokenizer, 128, 64))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load ONNX model");
    }
}
