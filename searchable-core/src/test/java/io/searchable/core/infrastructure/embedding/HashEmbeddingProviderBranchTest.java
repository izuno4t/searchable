package io.searchable.core.infrastructure.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Branch-coverage supplement for {@link HashEmbeddingProvider}. Targets the
 * input-validation rejections inside the constructor and the
 * {@code embed(null)} null-rejection short-circuit.
 */
class HashEmbeddingProviderBranchTest {

    @Test
    void constructorRejectsNonPositiveDimension() {
        assertThatThrownBy(() -> new HashEmbeddingProvider(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multiple of 8");
        assertThatThrownBy(() -> new HashEmbeddingProvider(-8))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsDimensionNotMultipleOf8() {
        assertThatThrownBy(() -> new HashEmbeddingProvider(7))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multiple of 8");
    }

    @Test
    void embedThrowsOnNullText() {
        final HashEmbeddingProvider p = new HashEmbeddingProvider(16);
        assertThatThrownBy(() -> p.embed(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void embedProducesNormalisedVector() {
        final HashEmbeddingProvider p = new HashEmbeddingProvider(16);
        final float[] v = p.embed("hello");
        double sum = 0.0;
        for (final float x : v) {
            sum += x * x;
        }
        assertThat(Math.sqrt(sum)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void identifierEncodesDimension() {
        assertThat(new HashEmbeddingProvider(384).identifier()).isEqualTo("hash:384");
    }

    @Test
    void dimensionGetterReturnsConfiguredValue() {
        assertThat(new HashEmbeddingProvider(64).dimension()).isEqualTo(64);
    }
}
