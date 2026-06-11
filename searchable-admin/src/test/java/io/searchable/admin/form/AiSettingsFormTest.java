package io.searchable.admin.form;

import io.searchable.ai.SummaryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage for {@link AiSettingsForm}. Targets the null-handling
 * branches inside {@code from(SummaryConfig)}, {@code toSummaryConfig()}, and
 * the setters that defensively coerce null input to an empty string.
 */
class AiSettingsFormTest {

    @Test
    void fromConfig_treatsNullProviderAndModelAsEmptyStrings() {
        // Disabled config returns null providerName/model from SummaryConfig.
        final AiSettingsForm form = AiSettingsForm.from(SummaryConfig.disabled());

        assertThat(form.getProvider()).isEmpty();
        assertThat(form.getModel()).isEmpty();
        assertThat(form.isEnabled()).isFalse();
        assertThat(form.getTimeoutSeconds())
            .isEqualTo(SummaryConfig.DEFAULT_TIMEOUT.getSeconds());
    }

    @Test
    void fromConfig_copiesAllScalarFields() {
        final SummaryConfig src = new SummaryConfig(
            "ollama", "llama3.2",
            Duration.ofSeconds(20), 256, 0.5, 3, 4000, false);

        final AiSettingsForm form = AiSettingsForm.from(src);

        // SummaryConfig.enabled() returns true for any non-blank provider, so
        // a config that names "ollama" round-trips into form.enabled=true.
        assertThat(form.isEnabled()).isTrue();
        assertThat(form.getProvider()).isEqualTo("ollama");
        assertThat(form.getModel()).isEqualTo("llama3.2");
        assertThat(form.getTimeoutSeconds()).isEqualTo(20L);
        assertThat(form.getMaxTokens()).isEqualTo(256);
        assertThat(form.getTemperature()).isEqualTo(0.5);
        assertThat(form.getMaxContextItems()).isEqualTo(3);
        assertThat(form.getMaxContextChars()).isEqualTo(4000);
        assertThat(form.isFallbackOnError()).isFalse();
    }

    @Test
    void toSummaryConfig_emitsNullProviderWhenDisabled() {
        final AiSettingsForm form = new AiSettingsForm();
        form.setEnabled(false);
        form.setProvider("openai");
        form.setModel("gpt-4o-mini");

        final SummaryConfig config = form.toSummaryConfig();

        // enabled=false suppresses the provider so SummaryService treats the
        // config as disabled even though the form retains the provider name.
        assertThat(config.providerName()).isNull();
        assertThat(config.model()).isEqualTo("gpt-4o-mini");
        assertThat(config.enabled()).isFalse();
    }

    @Test
    void toSummaryConfig_emitsNullProviderWhenBlank() {
        final AiSettingsForm form = new AiSettingsForm();
        form.setEnabled(true);
        form.setProvider("   ");
        form.setModel("");

        final SummaryConfig config = form.toSummaryConfig();

        assertThat(config.providerName()).isNull();
        assertThat(config.model()).isNull();
    }

    @Test
    void setProviderCoercesNullToEmptyString() {
        final AiSettingsForm form = new AiSettingsForm();
        form.setProvider(null);
        assertThat(form.getProvider()).isEmpty();
    }

    @Test
    void setModelCoercesNullToEmptyString() {
        final AiSettingsForm form = new AiSettingsForm();
        form.setModel(null);
        assertThat(form.getModel()).isEmpty();
    }

    @Test
    void primitiveSettersAndGettersAreReachable() {
        final AiSettingsForm form = new AiSettingsForm();
        form.setEnabled(true);
        form.setTimeoutSeconds(42L);
        form.setMaxTokens(128);
        form.setTemperature(1.5);
        form.setMaxContextItems(7);
        form.setMaxContextChars(1234);
        form.setFallbackOnError(false);

        assertThat(form.isEnabled()).isTrue();
        assertThat(form.getTimeoutSeconds()).isEqualTo(42L);
        assertThat(form.getMaxTokens()).isEqualTo(128);
        assertThat(form.getTemperature()).isEqualTo(1.5);
        assertThat(form.getMaxContextItems()).isEqualTo(7);
        assertThat(form.getMaxContextChars()).isEqualTo(1234);
        assertThat(form.isFallbackOnError()).isFalse();
    }
}
