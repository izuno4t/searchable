package io.searchable.example.api.controller.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.searchable.core.domain.search.SearchHit;

import java.util.List;
import java.util.Map;

/** Single hit entry rendered inside a search response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchHitPayload(
    String id,
    String namespaceId,
    String title,
    String content,
    double score,
    Map<String, List<String>> highlight,
    Map<String, Object> metadata
) {

    public static SearchHitPayload from(final SearchHit hit) {
        return new SearchHitPayload(hit.documentId(), hit.namespaceId(), hit.title(), hit.content(),
            hit.score(), hit.highlights(), hit.metadata());
    }
}
