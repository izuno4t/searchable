package io.searchable.testkit.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeEmbeddingProviderTest {

    @Test
    void defaultConstructorUsesDefaultDimension() {
        try (FakeEmbeddingProvider p = new FakeEmbeddingProvider()) {
            assertThat(p.dimension()).isEqualTo(FakeEmbeddingProvider.DEFAULT_DIMENSION);
            assertThat(p.embed("anything")).hasSize(FakeEmbeddingProvider.DEFAULT_DIMENSION);
        }
    }

    @Test
    void identifierIsPrefixedWithFake() {
        try (FakeEmbeddingProvider p = new FakeEmbeddingProvider(32)) {
            assertThat(p.identifier()).startsWith("fake:");
            assertThat(p.dimension()).isEqualTo(32);
        }
    }

    @Test
    void differentInputsProduceDifferentVectors() {
        try (FakeEmbeddingProvider p = new FakeEmbeddingProvider(64)) {
            assertThat(p.embed("alpha")).isNotEqualTo(p.embed("bravo"));
        }
    }
}
