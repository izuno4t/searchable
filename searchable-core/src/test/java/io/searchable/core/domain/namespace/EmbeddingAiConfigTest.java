package io.searchable.core.domain.namespace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingAiConfigTest {

    @Test
    void embeddingConfigRejectsNullOrBlankModel() {
        assertThatThrownBy(() -> new EmbeddingConfig(null, 128))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingConfig(" ", 128))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void embeddingConfigRejectsNonPositiveDimension() {
        assertThatThrownBy(() -> new EmbeddingConfig("m", 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingConfig("m", -5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiConfigDisabledHelperReturnsExpected() {
        final AiConfig disabled = AiConfig.disabled();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.provider()).isNull();
        assertThat(disabled.model()).isNull();
    }

    @Test
    void aiConfigEnabledRequiresProviderAndModel() {
        assertThatThrownBy(() -> new AiConfig(true, null, "model"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiConfig(true, "openai", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aiConfigEnabledHappyPath() {
        final AiConfig cfg = new AiConfig(true, "openai", "gpt-4");
        assertThat(cfg.enabled()).isTrue();
        assertThat(cfg.provider()).isEqualTo("openai");
        assertThat(cfg.model()).isEqualTo("gpt-4");
    }
}
