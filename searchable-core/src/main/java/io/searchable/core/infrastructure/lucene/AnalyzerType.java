package io.searchable.core.infrastructure.lucene;

import java.util.Locale;

/**
 * Selectable Japanese analyzer implementations.
 *
 * <p>The active analyzer is chosen via configuration
 * ({@code searchable.analyzer}). {@link #KUROMOJI} is the default and
 * always available; {@link #SUDACHI} requires the user to add the
 * {@code com.worksap.nlp:sudachi} + {@code lucene-analyzers-sudachi}
 * artifacts and a Sudachi system dictionary on the classpath.
 */
public enum AnalyzerType {

    /** Built-in Lucene Kuromoji analyzer (default). */
    KUROMOJI,

    /**
     * Sudachi-backed analyzer (loaded reflectively to keep Sudachi an
     * optional runtime dependency). Falls back to {@link #KUROMOJI}
     * with a warning when the Sudachi classes are not on the classpath.
     */
    SUDACHI;

    public static AnalyzerType from(final String value) {
        if (value == null || value.isBlank()) {
            return KUROMOJI;
        }
        return AnalyzerType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
