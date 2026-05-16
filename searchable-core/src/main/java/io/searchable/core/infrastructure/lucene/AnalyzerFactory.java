package io.searchable.core.infrastructure.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;

/**
 * Factory for the per-namespace {@link Analyzer}.
 *
 * <p>Phase 1 always returns Lucene's {@link JapaneseAnalyzer} (Kuromoji);
 * later phases may swap this implementation based on namespace configuration.
 */
public interface AnalyzerFactory {

    /** Create a new analyzer instance for the given namespace. */
    Analyzer create(String namespaceId);

    /** Default factory backed by Kuromoji {@link JapaneseAnalyzer}. */
    static AnalyzerFactory japanese() {
        return namespaceId -> new JapaneseAnalyzer();
    }

    /** Resolve a built-in factory by analyzer type. */
    static AnalyzerFactory forType(final AnalyzerType type) {
        return switch (type) {
            case KUROMOJI -> japanese();
            case SUDACHI -> new SudachiAnalyzerFactory();
        };
    }
}
