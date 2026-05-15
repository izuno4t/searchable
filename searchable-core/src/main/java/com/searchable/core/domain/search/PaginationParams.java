package com.searchable.core.domain.search;

/**
 * Pagination parameters for a search query.
 *
 * @param offset zero-based offset of the first hit to return
 * @param limit  maximum number of hits to return
 */
public record PaginationParams(int offset, int limit) {

    public PaginationParams {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative, was " + offset);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
    }

    public static PaginationParams defaults() {
        return new PaginationParams(0, 10);
    }
}
