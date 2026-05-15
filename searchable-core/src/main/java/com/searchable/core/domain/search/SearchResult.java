package com.searchable.core.domain.search;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a {@link SearchRequest}.
 *
 * @param hits           ranked hits (size &lt;= {@code pagination.limit()})
 * @param totalHits      total matching documents (may be approximate)
 * @param maxScore       maximum hit score (0 when {@code hits} is empty)
 * @param aggregations   facet / aggregation results (may be empty)
 * @param tookMs         time spent executing the search in milliseconds
 */
public record SearchResult(
    List<SearchHit> hits,
    long totalHits,
    double maxScore,
    Map<String, Object> aggregations,
    long tookMs
) {

    public SearchResult {
        Objects.requireNonNull(hits, "hits must not be null");
        if (totalHits < 0) {
            throw new IllegalArgumentException("totalHits must not be negative");
        }
        if (tookMs < 0) {
            throw new IllegalArgumentException("tookMs must not be negative");
        }
        hits = List.copyOf(hits);
        aggregations = aggregations == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(aggregations));
    }

    public static SearchResult empty(final long tookMs) {
        return new SearchResult(List.of(), 0L, 0.0, Map.of(), tookMs);
    }
}
