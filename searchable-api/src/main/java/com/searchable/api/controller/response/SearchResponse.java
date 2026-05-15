package com.searchable.api.controller.response;

import com.searchable.api.controller.payload.SearchHitPayload;
import com.searchable.core.domain.search.SearchResult;

import java.util.List;
import java.util.Map;

/** Response for {@code POST /api/v1/search}. */
public record SearchResponse(
    List<SearchHitPayload> hits,
    long totalHits,
    double maxScore,
    Map<String, Object> aggregations,
    long took
) {

    public static SearchResponse from(final SearchResult result) {
        return new SearchResponse(
            result.hits().stream().map(SearchHitPayload::from).toList(),
            result.totalHits(),
            result.maxScore(),
            result.aggregations(),
            result.tookMs()
        );
    }
}
