package com.searchable.api.controller.request;

import com.searchable.api.controller.payload.SearchOptionsPayload;
import com.searchable.core.domain.search.PaginationParams;
import com.searchable.core.domain.search.SearchOptions;
import com.searchable.core.domain.search.SearchType;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/** Body for {@code POST /api/v1/search}. */
public record SearchRequest(
    @NotBlank String query,
    List<String> namespaceIds,
    SearchType searchType,
    SearchOptionsPayload options,
    Map<String, Object> filters
) {

    public com.searchable.core.domain.search.SearchRequest toDomain() {
        final boolean highlight = options == null || options.highlightEnabled() == null
            || options.highlightEnabled();
        final int offset = options == null || options.offset() == null ? 0 : options.offset();
        final int limit = options == null || options.maxResults() == null ? 10 : options.maxResults();
        return com.searchable.core.domain.search.SearchRequest.builder()
            .query(query)
            .namespaceIds(namespaceIds == null ? List.of() : namespaceIds)
            .searchType(searchType)
            .options(new SearchOptions(highlight))
            .pagination(new PaginationParams(offset, limit))
            .filters(filters == null ? Map.of() : filters)
            .build();
    }
}
