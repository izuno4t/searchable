package io.searchable.core.infrastructure.chunking;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadingWeightsTest {

    @Test
    void baseWeightsFollowRequiredSchedule() {
        final HeadingWeights w = HeadingWeights.defaults();
        assertThat(w.weightFor(1)).isEqualTo(7.0);
        assertThat(w.weightFor(2)).isEqualTo(6.0);
        assertThat(w.weightFor(3)).isEqualTo(5.0);
        assertThat(w.weightFor(4)).isEqualTo(4.0);
        assertThat(w.weightFor(5)).isEqualTo(3.0);
        assertThat(w.weightFor(6)).isEqualTo(2.0);
    }

    @Test
    void outOfRangeLevelsFallBackToDefault() {
        final HeadingWeights w = HeadingWeights.defaults();
        assertThat(w.weightFor(0)).isEqualTo(HeadingWeights.DEFAULT_WEIGHT);
        assertThat(w.weightFor(7)).isEqualTo(HeadingWeights.DEFAULT_WEIGHT);
    }

    @Test
    void quadraticScalingProducesAbout4xAtWeight2() {
        // Requirement: weight 2.0 -> ~4x effect.
        assertThat(HeadingWeights.effectiveBoost(2.0)).isEqualTo(4.0);
        assertThat(HeadingWeights.effectiveBoost(3.0)).isEqualTo(9.0);
        assertThat(HeadingWeights.effectiveBoost(7.0)).isEqualTo(49.0);
    }

    @Test
    void repetitionsForWeightDeriveFromQuadratic() {
        // base weight 2.0 -> effective 4 -> 3 extra repetitions on top of the original
        assertThat(HeadingWeights.repetitionsForWeight(2.0)).isEqualTo(3);
        assertThat(HeadingWeights.repetitionsForWeight(7.0)).isEqualTo(48);
        assertThat(HeadingWeights.repetitionsForWeight(0.0)).isZero();
    }

    @Test
    void overridesReplaceTheDefaultSchedule() {
        final HeadingWeights custom = HeadingWeights.withOverrides(Map.of(1, 3.0, 2, 1.5));
        assertThat(custom.weightFor(1)).isEqualTo(3.0);
        assertThat(custom.weightFor(2)).isEqualTo(1.5);
        // Unmapped levels fall back to the default schedule
        assertThat(custom.weightFor(3)).isEqualTo(5.0);
    }

    @Test
    void overridesEnforceLegalRange() {
        assertThatThrownBy(() -> HeadingWeights.withOverrides(Map.of(1, 11.0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("level 1");
        assertThatThrownBy(() -> HeadingWeights.withOverrides(Map.of(1, -0.1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
