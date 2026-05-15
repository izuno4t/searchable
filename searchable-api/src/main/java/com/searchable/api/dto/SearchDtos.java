package com.searchable.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.searchable.core.domain.search.PaginationParams;
import com.searchable.core.domain.search.SearchHit;
import com.searchable.core.domain.search.SearchOptions;
import com.searchable.core.domain.search.SearchRequest;
import com.searchable.core.domain.search.SearchResult;
import com.searchable.core.domain.search.SearchType;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Container for search-related DTOs.
 */
public final class SearchDtos {

    private SearchDtos() { }

    public record OptionsDto(Boolean highlightEnabled, Integer maxResults, Integer offset) { }

    public record Request(@NotBlank String query,
                          List<String> namespaceIds,
                          SearchType searchType,
                          OptionsDto options,
                          Map<String, Object> filters) {

        public SearchRequest toDomain() {
            final SearchOptions opts = new SearchOptions(
                options == null || options.highlightEnabled() == null
                    ? true : options.highlightEnabled());
            final int offset = options == null || options.offset() == null ? 0 : options.offset();
            final int limit = options == null || options.maxResults() == null ? 10 : options.maxResults();
            return SearchRequest.builder()
                .query(query)
                .namespaceIds(namespaceIds == null ? List.of() : namespaceIds)
                .searchType(searchType)
                .options(opts)
                .pagination(new PaginationParams(offset, limit))
                .filters(filters == null ? Map.of() : filters)
                .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HitDto(String id, String namespaceId, String title, String content,
                         double score, Map<String, List<String>> highlight,
                         Map<String, Object> metadata) {
        public static HitDto from(final SearchHit hit) {
            return new HitDto(hit.documentId(), hit.namespaceId(), hit.title(), hit.content(),
                hit.score(), hit.highlights(), hit.metadata());
        }
    }

    public record Response(List<HitDto> hits, long totalHits, double maxScore,
                           Map<String, Object> aggregations, long took) {
        public static Response from(final SearchResult result) {
            return new Response(
                result.hits().stream().map(HitDto::from).toList(),
                result.totalHits(),
                result.maxScore(),
                result.aggregations(),
                result.tookMs()
            );
        }
    }
}
