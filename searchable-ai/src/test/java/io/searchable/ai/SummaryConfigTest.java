package io.searchable.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage for {@link SummaryConfig}'s compact constructor branches.
 *
 * <p>SummaryServiceTest covers the happy paths; this class targets the
 * defensive validation logic: default-application for non-positive numeric
 * fields, rejection of non-positive timeout, and rejection of out-of-range
 * temperature.
 */
class SummaryConfigTest {

    @Test
    void enabled_falseForNullProvider() {
        assertThat(SummaryConfig.disabled().enabled()).isFalse();
    }

    @Test
    void enabled_falseForBlankProvider() {
        final SummaryConfig blank = new SummaryConfig(
            "   ", null, Duration.ofSeconds(1), 1, 0.0, 1, 1, true);
        assertThat(blank.enabled()).isFalse();
    }

    @Test
    void enabled_trueForNonBlankProvider() {
        assertThat(SummaryConfig.forProvider("openai").enabled()).isTrue();
    }

    @Test
    void nullTimeoutReplacedWithDefault() {
        final SummaryConfig config = new SummaryConfig(
            null, null, null, 1, 0.0, 1, 1, true);
        assertThat(config.timeout()).isEqualTo(SummaryConfig.DEFAULT_TIMEOUT);
    }

    @Test
    void zeroTimeoutRejected() {
        assertThatThrownBy(() -> new SummaryConfig(
            null, null, Duration.ZERO, 1, 0.0, 1, 1, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeout");
    }

    @Test
    void negativeTimeoutRejected() {
        assertThatThrownBy(() -> new SummaryConfig(
            null, null, Duration.ofSeconds(-1), 1, 0.0, 1, 1, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeout");
    }

    @Test
    void nonPositiveMaxTokensReplacedWithDefault() {
        final SummaryConfig config = new SummaryConfig(
            null, null, Duration.ofSeconds(1), 0, 0.0, 1, 1, true);
        assertThat(config.maxTokens()).isEqualTo(SummaryConfig.DEFAULT_MAX_TOKENS);
    }

    @Test
    void nonPositiveMaxContextItemsReplacedWithDefault() {
        final SummaryConfig config = new SummaryConfig(
            null, null, Duration.ofSeconds(1), 1, 0.0, -3, 1, true);
        assertThat(config.maxContextItems()).isEqualTo(SummaryConfig.DEFAULT_MAX_CONTEXT_ITEMS);
    }

    @Test
    void nonPositiveMaxContextCharsReplacedWithDefault() {
        final SummaryConfig config = new SummaryConfig(
            null, null, Duration.ofSeconds(1), 1, 0.0, 1, 0, true);
        assertThat(config.maxContextChars()).isEqualTo(SummaryConfig.DEFAULT_MAX_CONTEXT_CHARS);
    }

    @Test
    void temperatureBelowZeroRejected() {
        assertThatThrownBy(() -> new SummaryConfig(
            null, null, Duration.ofSeconds(1), 1, -0.1, 1, 1, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("temperature");
    }

    @Test
    void temperatureAboveTwoRejected() {
        assertThatThrownBy(() -> new SummaryConfig(
            null, null, Duration.ofSeconds(1), 1, 2.5, 1, 1, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("temperature");
    }

    @Test
    void forProviderRejectsNull() {
        assertThatThrownBy(() -> SummaryConfig.forProvider(null))
            .isInstanceOf(NullPointerException.class);
    }
}
