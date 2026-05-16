package io.searchable.core.application;

import io.searchable.core.domain.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FacetFilterTest {

    @Test
    void emptyFilterReturnsAllHits() {
        final List<SearchHit> hits = List.of(hit("d1", "ns", Map.of()));
        assertThat(FacetFilter.apply(hits, Map.of())).hasSize(1);
    }

    @Test
    void singleKeySingleValueIsAnEqualityFilter() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of("category", "blog")),
            hit("d2", "ns", Map.of("category", "news")));
        final var filtered = FacetFilter.apply(hits, Map.of("category", "blog"));
        assertThat(filtered).extracting(SearchHit::documentId).containsExactly("d1");
    }

    @Test
    void listValueImpliesOrWithinAField() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of("category", "blog")),
            hit("d2", "ns", Map.of("category", "news")),
            hit("d3", "ns", Map.of("category", "internal")));
        final var filtered = FacetFilter.apply(hits,
            Map.of("category", List.of("blog", "internal")));
        assertThat(filtered).extracting(SearchHit::documentId).containsExactly("d1", "d3");
    }

    @Test
    void multipleKeysAreAndCombined() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of("category", "blog", "lang", "ja")),
            hit("d2", "ns", Map.of("category", "blog", "lang", "en")),
            hit("d3", "ns", Map.of("category", "news", "lang", "ja")));
        final var filtered = FacetFilter.apply(hits,
            Map.of("category", "blog", "lang", "ja"));
        assertThat(filtered).extracting(SearchHit::documentId).containsExactly("d1");
    }

    @Test
    void reservedNamespaceKeyMatchesNamespaceId() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns-a", Map.of()),
            hit("d2", "ns-b", Map.of()));
        final var filtered = FacetFilter.apply(hits,
            Map.of(FacetFilter.NAMESPACE_KEY, "ns-a"));
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).namespaceId()).isEqualTo("ns-a");
    }

    @Test
    void listMetadataValuesAreMatchedAnyElement() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of("tags", List.of("java", "search"))),
            hit("d2", "ns", Map.of("tags", List.of("python"))));
        final var filtered = FacetFilter.apply(hits, Map.of("tags", "search"));
        assertThat(filtered).extracting(SearchHit::documentId).containsExactly("d1");
    }

    private SearchHit hit(final String id, final String ns,
                          final Map<String, Object> metadata) {
        return new SearchHit(id, ns, "title", "content", 1.0, Map.of(), metadata);
    }
}
