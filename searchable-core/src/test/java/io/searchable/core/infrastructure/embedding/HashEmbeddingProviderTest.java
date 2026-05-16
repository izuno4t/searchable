package io.searchable.core.infrastructure.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashEmbeddingProviderTest {

    @Test
    void producesVectorOfRequestedDimension() {
        final HashEmbeddingProvider provider = new HashEmbeddingProvider(384);
        assertThat(provider.embed("hello")).hasSize(384);
        assertThat(provider.dimension()).isEqualTo(384);
    }

    @Test
    void sameInputProducesSameVector() {
        final HashEmbeddingProvider provider = new HashEmbeddingProvider(128);
        assertThat(provider.embed("日本語テキスト"))
            .containsExactly(provider.embed("日本語テキスト"));
    }

    @Test
    void differentInputsProduceDifferentVectors() {
        final HashEmbeddingProvider provider = new HashEmbeddingProvider(128);
        assertThat(provider.embed("foo")).isNotEqualTo(provider.embed("bar"));
    }

    @Test
    void outputIsL2Normalized() {
        final HashEmbeddingProvider provider = new HashEmbeddingProvider(64);
        final float[] vector = provider.embed("normalize me");
        double sum = 0.0;
        for (final float v : vector) {
            sum += (double) v * v;
        }
        assertThat(Math.sqrt(sum)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void rejectsNonMultipleOfEightDimension() {
        assertThatThrownBy(() -> new HashEmbeddingProvider(10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroDimension() {
        assertThatThrownBy(() -> new HashEmbeddingProvider(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
