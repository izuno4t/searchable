package com.searchable.core.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Records the latency of each completed search and exposes summary
 * statistics for the dashboard.
 *
 * <p>Stores up to {@code capacity} most recent samples in a circular buffer.
 * Thread-safe for concurrent recording and reading.
 */
public final class SearchPerformanceMonitor {

    private final int capacity;
    private final long[] latenciesMs;
    private final long[] timestampsMs;
    private final ReentrantLock lock = new ReentrantLock();
    private final Clock clock;
    private int head;
    private int size;

    public SearchPerformanceMonitor() {
        this(1024, Clock.systemUTC());
    }

    public SearchPerformanceMonitor(final int capacity, final Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.latenciesMs = new long[capacity];
        this.timestampsMs = new long[capacity];
        this.clock = Objects.requireNonNull(clock);
    }

    public void record(final long latencyMs) {
        if (latencyMs < 0) {
            return;
        }
        lock.lock();
        try {
            latenciesMs[head] = latencyMs;
            timestampsMs[head] = clock.millis();
            head = (head + 1) % capacity;
            if (size < capacity) {
                size++;
            }
        } finally {
            lock.unlock();
        }
    }

    public Summary summary() {
        lock.lock();
        try {
            if (size == 0) {
                return new Summary(0L, 0L, 0L, 0L, 0L, 0L, 0.0, List.of());
            }
            final long[] sorted = new long[size];
            int idx = (head - size + capacity) % capacity;
            final List<Sample> samples = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                sorted[i] = latenciesMs[idx];
                samples.add(new Sample(Instant.ofEpochMilli(timestampsMs[idx]), latenciesMs[idx]));
                idx = (idx + 1) % capacity;
            }
            Arrays.sort(sorted);
            final long min = sorted[0];
            final long max = sorted[sorted.length - 1];
            final long p50 = sorted[(int) (sorted.length * 0.5)];
            final long p95 = sorted[(int) Math.min(sorted.length - 1, sorted.length * 0.95)];
            final long p99 = sorted[(int) Math.min(sorted.length - 1, sorted.length * 0.99)];
            double sum = 0.0;
            for (final long v : sorted) {
                sum += v;
            }
            return new Summary((long) size, min, p50, p95, p99, max, sum / size, samples);
        } finally {
            lock.unlock();
        }
    }

    public record Summary(long count, long min, long p50, long p95, long p99, long max,
                          double avg, List<Sample> samples) { }

    public record Sample(Instant timestamp, long latencyMs) { }
}
