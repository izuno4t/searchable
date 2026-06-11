package io.searchable.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct coverage for {@link SummaryConfigProvider}. The Spring-driven path
 * is exercised indirectly by {@code AiSettingsControllerTest}; this class
 * pins down the explicit null-rejection branches inside the constructor and
 * {@link SummaryConfigProvider#update(SummaryConfig)}.
 */
class SummaryConfigProviderTest {

    @Test
    void currentReturnsInitialConfig() {
        final SummaryConfigProvider p = new SummaryConfigProvider(SummaryConfig.disabled());
        assertThat(p.current()).isSameAs(p.current());
        assertThat(p.current().enabled()).isFalse();
    }

    @Test
    void updateReplacesActiveConfig() {
        final SummaryConfigProvider p = new SummaryConfigProvider(SummaryConfig.disabled());
        final SummaryConfig next = SummaryConfig.forProvider("openai");
        p.update(next);
        assertThat(p.current()).isSameAs(next);
    }

    @Test
    void constructorRejectsNullInitial() {
        assertThatThrownBy(() -> new SummaryConfigProvider(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("initial");
    }

    @Test
    void updateRejectsNull() {
        final SummaryConfigProvider p = new SummaryConfigProvider(SummaryConfig.disabled());
        assertThatThrownBy(() -> p.update(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("next");
    }
}
