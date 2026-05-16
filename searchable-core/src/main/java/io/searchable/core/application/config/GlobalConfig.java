package io.searchable.core.application.config;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import io.searchable.core.infrastructure.lucene.AnalyzerType;

import java.util.Objects;

/**
 * Global defaults applied when a namespace does not specify its own value.
 *
 * <p>{@code analyzer} chooses the active Japanese analyzer implementation
 * (TASK-023). {@link AnalyzerType#KUROMOJI} is the built-in default;
 * {@link AnalyzerType#SUDACHI} requires the optional Sudachi runtime to
 * be on the classpath.
 */
public record GlobalConfig(
    SearchType defaultArchitecture,
    SearchStrategy defaultSearchStrategy,
    SearchOrder defaultSearchOrder,
    AnalyzerType analyzer
) {

    public GlobalConfig {
        Objects.requireNonNull(defaultArchitecture, "defaultArchitecture must not be null");
        Objects.requireNonNull(defaultSearchStrategy, "defaultSearchStrategy must not be null");
        Objects.requireNonNull(defaultSearchOrder, "defaultSearchOrder must not be null");
        analyzer = analyzer == null ? AnalyzerType.KUROMOJI : analyzer;
    }

    /** Backward-compatible constructor without {@code analyzer}. */
    public GlobalConfig(final SearchType defaultArchitecture,
                        final SearchStrategy defaultSearchStrategy,
                        final SearchOrder defaultSearchOrder) {
        this(defaultArchitecture, defaultSearchStrategy, defaultSearchOrder,
            AnalyzerType.KUROMOJI);
    }

    public static GlobalConfig defaults() {
        return new GlobalConfig(
            SearchType.FULL_TEXT,
            SearchStrategy.SEQUENTIAL,
            SearchOrder.FULL_TEXT_FIRST,
            AnalyzerType.KUROMOJI
        );
    }
}
