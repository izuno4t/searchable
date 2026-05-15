package com.searchable.core.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPerformanceMonitorTest {

    @Test
    void emptyMonitorReturnsZeros() {
        final SearchPerformanceMonitor monitor = new SearchPerformanceMonitor();
        final SearchPerformanceMonitor.Summary s = monitor.summary();
        assertThat(s.count()).isZero();
        assertThat(s.samples()).isEmpty();
    }

    @Test
    void summaryReflectsRecordedLatencies() {
        final SearchPerformanceMonitor monitor = new SearchPerformanceMonitor();
        monitor.record(10);
        monitor.record(20);
        monitor.record(30);
        monitor.record(40);
        monitor.record(50);

        final SearchPerformanceMonitor.Summary s = monitor.summary();
        assertThat(s.count()).isEqualTo(5L);
        assertThat(s.min()).isEqualTo(10L);
        assertThat(s.max()).isEqualTo(50L);
        assertThat(s.p50()).isEqualTo(30L);
        assertThat(s.avg()).isEqualTo(30.0);
    }

    @Test
    void capacityIsRespectedViaCircularBuffer() {
        final SearchPerformanceMonitor monitor = new SearchPerformanceMonitor(3,
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneId.of("UTC")));
        monitor.record(1);
        monitor.record(2);
        monitor.record(3);
        monitor.record(4);

        final SearchPerformanceMonitor.Summary s = monitor.summary();
        assertThat(s.count()).isEqualTo(3L);
        assertThat(s.samples()).extracting(SearchPerformanceMonitor.Sample::latencyMs)
            .containsExactly(2L, 3L, 4L);
    }

    @Test
    void negativeLatenciesAreIgnored() {
        final SearchPerformanceMonitor monitor = new SearchPerformanceMonitor();
        monitor.record(-5);
        monitor.record(10);
        assertThat(monitor.summary().count()).isEqualTo(1L);
    }
}
