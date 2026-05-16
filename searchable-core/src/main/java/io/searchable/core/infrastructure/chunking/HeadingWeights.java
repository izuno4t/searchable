package io.searchable.core.infrastructure.chunking;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves the relevance multiplier applied to a section based on its
 * heading level.
 *
 * <p>Base weights (TASK-027): {@code h1 = 7.0}, {@code h2 = 6.0},
 * ..., {@code h6 = 2.0}. Levels outside {@code [1, 6]} fall back to
 * {@code 1.0} (no boost).
 *
 * <p>Effective boost (TASK-029): {@code f(w) = w * w}. The requirement
 * states that {@code w = 2.0} should yield roughly a 4&times; effect, so a
 * quadratic curve is the natural fit. The result is realized by repeating
 * the heading text inside the chunk; this raises the term frequency
 * Lucene sees and consequently the BM25 score for that section.
 *
 * <p>Callers can plug in custom weights via {@link #withOverrides(Map)} —
 * this is the public API for TASK-028. Overrides must lie within
 * {@code [0.0, 10.0]}.
 */
public final class HeadingWeights {

    public static final double DEFAULT_WEIGHT = 1.0;
    public static final double MAX_WEIGHT = 10.0;

    private static final HeadingWeights DEFAULT_INSTANCE = new HeadingWeights(Map.of());

    private final Map<Integer, Double> overrides;

    private HeadingWeights(final Map<Integer, Double> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    /** Default schedule (h1=7 ... h6=2). */
    public static HeadingWeights defaults() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Custom schedule layered on top of the defaults.
     *
     * @param overrides level → weight pairs ({@code 0.0 <= weight <= 10.0});
     *                  unmapped levels fall back to the default schedule
     * @throws IllegalArgumentException if any weight is outside the legal range
     */
    public static HeadingWeights withOverrides(final Map<Integer, Double> overrides) {
        Objects.requireNonNull(overrides, "overrides must not be null");
        for (final Map.Entry<Integer, Double> e : overrides.entrySet()) {
            final double w = e.getValue();
            if (Double.isNaN(w) || w < 0.0 || w > MAX_WEIGHT) {
                throw new IllegalArgumentException(
                    "weight for level " + e.getKey() + " must be in [0.0, "
                        + MAX_WEIGHT + "], was " + w);
            }
        }
        return new HeadingWeights(overrides);
    }

    /**
     * @param level 1 for {@code h1}, 2 for {@code h2}, ..., 6 for {@code h6};
     *              any other value yields {@link #DEFAULT_WEIGHT}
     */
    public double weightFor(final int level) {
        final Double override = overrides.get(level);
        if (override != null) {
            return override;
        }
        return baseWeightFor(level);
    }

    /** Static convenience shortcut for the default schedule. */
    public static double baseWeightFor(final int level) {
        return switch (level) {
            case 1 -> 7.0;
            case 2 -> 6.0;
            case 3 -> 5.0;
            case 4 -> 4.0;
            case 5 -> 3.0;
            case 6 -> 2.0;
            default -> DEFAULT_WEIGHT;
        };
    }

    /**
     * Apply the quadratic scaling: {@code effectiveBoost(w) = w * w}.
     * Public so callers can derive the same effect when implementing
     * custom scorers.
     */
    public static double effectiveBoost(final double weight) {
        return weight * weight;
    }

    /**
     * Number of additional heading repetitions to inject so the chunk's
     * term frequency tracks the {@link #effectiveBoost(double) quadratic}
     * boost. Always non-negative; the original occurrence is implicit.
     */
    public int repetitionsFor(final int level) {
        return repetitionsForWeight(weightFor(level));
    }

    /** Same as {@link #repetitionsFor(int)} but driven by an explicit weight. */
    public static int repetitionsForWeight(final double weight) {
        return Math.max(0, (int) Math.round(effectiveBoost(weight)) - 1);
    }
}
