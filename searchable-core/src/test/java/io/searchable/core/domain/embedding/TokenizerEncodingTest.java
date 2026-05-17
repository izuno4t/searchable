package io.searchable.core.domain.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenizerEncodingTest {

    @Test
    void encodingRejectsLengthMismatch() {
        assertThatThrownBy(() ->
            new Tokenizer.Encoding(new long[]{1L, 2L}, new long[]{1L}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodingExposesArrays() {
        final long[] ids = {1L, 2L, 3L};
        final long[] mask = {1L, 1L, 0L};
        final Tokenizer.Encoding enc = new Tokenizer.Encoding(ids, mask);
        assertThat(enc.inputIds()).isSameAs(ids);
        assertThat(enc.attentionMask()).isSameAs(mask);
    }

    @Test
    void embeddingProviderDefaultsHaveSensibleBehavior() {
        final EmbeddingProvider provider = new EmbeddingProvider() {
            @Override public float[] embed(final String text) { return new float[]{(float) text.length()}; }
            @Override public int dimension() { return 1; }
            @Override public String identifier() { return "stub"; }
        };

        // close() default impl is a no-op
        provider.close();

        // embedAll default impl
        final List<float[]> all = provider.embedAll(List.of("a", "bc"));
        assertThat(all).hasSize(2);
        assertThat(all.get(0)[0]).isEqualTo(1.0f);
        assertThat(all.get(1)[0]).isEqualTo(2.0f);
    }
}
