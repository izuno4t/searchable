package io.searchable.core.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Constructor-validation branch coverage for {@link SearchPerformanceMonitor}. */
class SearchPerformanceMonitorBranchTest {

    @Test
    void zeroCapacityRejected() {
        assertThatThrownBy(() -> new SearchPerformanceMonitor(0, Clock.systemUTC()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("capacity");
    }

    @Test
    void negativeCapacityRejected() {
        assertThatThrownBy(() -> new SearchPerformanceMonitor(-3, Clock.systemUTC()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
