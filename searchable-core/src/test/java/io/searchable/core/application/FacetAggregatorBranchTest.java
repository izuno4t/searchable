package io.searchable.core.application;

import io.searchable.core.domain.search.FacetSpec;
import io.searchable.core.domain.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Branch coverage helpers for {@link FacetAggregator}. */
class FacetAggregatorBranchTest {

    private SearchHit hit(final Map<String, Object> meta, final String content) {
        return new SearchHit("d", "ns", "t", content == null ? "" : content,
            1.0, Map.of(), meta);
    }

    @Test
    void nullOrEmptySpecsReturnEmptyMap() {
        assertThat(FacetAggregator.aggregate(List.of(), null)).isEmpty();
        assertThat(FacetAggregator.aggregate(List.of(), List.of())).isEmpty();
    }

    @Test
    void inlineReturnsEmptyForReservedNamespaceFacetField() {
        final List<SearchHit> hits = List.of(hit(Map.of("category", "blog"), null));
        final var spec = new FacetSpec(FacetAggregator.NAMESPACE_FACET,
            FacetAggregator.NAMESPACE_FACET, FacetSpec.Mode.INLINE);
        assertThat(FacetAggregator.aggregate(hits, List.of(spec)))
            .containsKey(FacetAggregator.NAMESPACE_FACET);
        // Reserved field returns empty list -> no counts collected
        assertThat(FacetAggregator.aggregate(hits, List.of(spec)).get(FacetAggregator.NAMESPACE_FACET))
            .isEmpty();
    }

    @Test
    void inlineMissingFieldReturnsEmpty() {
        final List<SearchHit> hits = List.of(hit(Map.of(), null));
        final var spec = FacetSpec.inline("never-present");
        assertThat(FacetAggregator.aggregate(hits, List.of(spec)).get("never-present"))
            .isEmpty();
    }

    @Test
    void attributeReturnsEmptyWhenIntermediateNotMap() {
        final Map<String, Object> meta = new HashMap<>();
        meta.put("author", "rooted-as-string");
        final List<SearchHit> hits = List.of(hit(meta, null));
        final var spec = FacetSpec.attribute("a", "author.name");
        assertThat(FacetAggregator.aggregate(hits, List.of(spec)).get("a")).isEmpty();
    }

    @Test
    void attributeReturnsEmptyWhenLeafMissing() {
        final List<SearchHit> hits = List.of(hit(Map.of("author", Map.of()), null));
        final var spec = FacetSpec.attribute("a", "author.name");
        assertThat(FacetAggregator.aggregate(hits, List.of(spec)).get("a")).isEmpty();
    }

    @Test
    void contentReturnsEmptyForBlankContentOrTagKey() {
        final List<SearchHit> hits = List.of(hit(Map.of(), "  "));
        final var spec = FacetSpec.content("genre", "genre");
        assertThat(FacetAggregator.aggregate(hits, List.of(spec)).get("genre")).isEmpty();
    }

    @Test
    void contentFindsAllMatchesIncludingMultipleSameKey() {
        final List<SearchHit> hits = List.of(hit(Map.of(),
            "前文 [tag:alpha] 本文 [tag:beta] その他 [tag:alpha]"));
        final var spec = FacetSpec.content("tag", "tag");
        final var counts = FacetAggregator.aggregate(hits, List.of(spec)).get("tag");
        assertThat(counts).containsEntry("alpha", 2L).containsEntry("beta", 1L);
    }

    @Test
    void flattenHandlesListValuesAndNullsInsideList() {
        final List<SearchHit> hits = List.of(hit(Map.of("tags",
            java.util.Arrays.asList("a", null, "b")), null));
        final var counts = FacetAggregator.aggregate(hits, List.of(FacetSpec.inline("tags")))
            .get("tags");
        assertThat(counts).containsOnlyKeys("a", "b");
    }
}
