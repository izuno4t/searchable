package io.searchable.core.infrastructure.chunking;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadingWeightsExtraTest {

    @Test
    void repetitionsForLevelDelegatesToWeight() {
        // h1 default weight 7 -> effective 49 -> 48 extra repetitions
        assertThat(HeadingWeights.defaults().repetitionsFor(1)).isEqualTo(48);
        // level 0 -> default weight 1.0 -> effective 1 -> 0 extra reps
        assertThat(HeadingWeights.defaults().repetitionsFor(0)).isZero();
    }

    @Test
    void overridesRejectNaNWeight() {
        assertThatThrownBy(() -> HeadingWeights.withOverrides(Map.of(1, Double.NaN)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("level 1");
    }

    @Test
    void overridesRejectNullMap() {
        assertThatThrownBy(() -> HeadingWeights.withOverrides(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void baseWeightForExposedStatically() {
        assertThat(HeadingWeights.baseWeightFor(1)).isEqualTo(7.0);
        assertThat(HeadingWeights.baseWeightFor(99)).isEqualTo(HeadingWeights.DEFAULT_WEIGHT);
    }

    @Test
    void repetitionsForWeightNeverNegative() {
        // Below 1.0 the rounded boost (0) - 1 would be negative; clamp guards it.
        assertThat(HeadingWeights.repetitionsForWeight(0.4)).isZero();
    }
}
