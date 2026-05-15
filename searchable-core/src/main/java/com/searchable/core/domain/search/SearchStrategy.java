package com.searchable.core.domain.search;

/**
 * Strategy for executing hybrid search.
 */
public enum SearchStrategy {
    /** Execute full-text and vector search sequentially. */
    SEQUENTIAL,
    /** Execute full-text and vector search in parallel and merge results. */
    PARALLEL
}
