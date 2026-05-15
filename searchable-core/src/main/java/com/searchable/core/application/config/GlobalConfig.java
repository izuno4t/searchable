package com.searchable.core.application.config;

import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;

import java.util.Objects;

/**
 * Global defaults applied when a namespace does not specify its own value.
 */
public record GlobalConfig(
    SearchType defaultArchitecture,
    SearchStrategy defaultSearchStrategy,
    SearchOrder defaultSearchOrder
) {

    public GlobalConfig {
        Objects.requireNonNull(defaultArchitecture, "defaultArchitecture must not be null");
        Objects.requireNonNull(defaultSearchStrategy, "defaultSearchStrategy must not be null");
        Objects.requireNonNull(defaultSearchOrder, "defaultSearchOrder must not be null");
    }

    public static GlobalConfig defaults() {
        return new GlobalConfig(
            SearchType.FULL_TEXT,
            SearchStrategy.SEQUENTIAL,
            SearchOrder.FULL_TEXT_FIRST
        );
    }
}
