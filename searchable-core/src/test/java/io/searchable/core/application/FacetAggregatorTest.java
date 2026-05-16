package io.searchable.core.application;

import io.searchable.core.domain.search.FacetSpec;
import io.searchable.core.domain.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FacetAggregatorTest {

    @Test
    void inlineFacetCountsRepeatedValues() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of("category", "blog")),
            hit("d2", "ns", Map.of("category", "blog")),
            hit("d3", "ns", Map.of("category", "news")));

        final Map<String, Map<String, Long>> agg = FacetAggregator.aggregate(
            hits, List.of(FacetSpec.inline("category")));

        assertThat(agg).containsOnlyKeys("category");
        assertThat(agg.get("category")).containsEntry("blog", 2L).containsEntry("news", 1L);
    }

    @Test
    void inlineFacetSupportsListValues() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of("tags", List.of("a", "b"))),
            hit("d2", "ns", Map.of("tags", List.of("b"))));
        final var agg = FacetAggregator.aggregate(hits, List.of(FacetSpec.inline("tags")));
        assertThat(agg.get("tags")).containsEntry("a", 1L).containsEntry("b", 2L);
    }

    @Test
    void attributeModeWalksDotPath() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of("author", Map.of("name", "佐藤"))),
            hit("d2", "ns", Map.of("author", Map.of("name", "鈴木"))));
        final var agg = FacetAggregator.aggregate(hits,
            List.of(FacetSpec.attribute("author", "author.name")));
        assertThat(agg.get("author")).containsEntry("佐藤", 1L).containsEntry("鈴木", 1L);
    }

    @Test
    void contentModeExtractsTagValues() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", "## 前書き\n[category:blog] 本文", Map.of()),
            hit("d2", "ns", "[category:news][tag:Java] body", Map.of()));
        final var agg = FacetAggregator.aggregate(hits,
            List.of(FacetSpec.content("category", "category")));
        assertThat(agg.get("category"))
            .containsEntry("blog", 1L)
            .containsEntry("news", 1L);
    }

    @Test
    void missingFieldsContributeZeroEntries() {
        final var agg = FacetAggregator.aggregate(
            List.of(hit("d1", "ns", Map.of())),
            List.of(FacetSpec.inline("missing")));
        assertThat(agg).containsKey("missing");
        assertThat(agg.get("missing")).isEmpty();
    }

    private SearchHit hit(final String id, final String ns,
                          final Map<String, Object> metadata) {
        return new SearchHit(id, ns, "title", "content", 1.0, Map.of(), metadata);
    }

    private SearchHit hit(final String id, final String ns, final String content,
                          final Map<String, Object> metadata) {
        return new SearchHit(id, ns, "title", content, 1.0, Map.of(), metadata);
    }
}
