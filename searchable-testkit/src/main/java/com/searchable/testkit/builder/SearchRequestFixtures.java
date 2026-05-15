package com.searchable.testkit.builder;

import com.searchable.core.domain.search.SearchRequest;

import java.util.List;

/** Pre-configured {@link SearchRequest.Builder} factories. */
public final class SearchRequestFixtures {

    private SearchRequestFixtures() { }

    /** Builder targeting the test namespace; only {@code query} preset. */
    public static SearchRequest.Builder builder(final String query) {
        return SearchRequest.builder()
            .query(query)
            .namespaceIds(List.of(DocumentFixtures.DEFAULT_NAMESPACE));
    }

    public static SearchRequest.Builder builder(final String query, final String namespaceId) {
        return SearchRequest.builder()
            .query(query)
            .namespaceIds(List.of(namespaceId));
    }
}
