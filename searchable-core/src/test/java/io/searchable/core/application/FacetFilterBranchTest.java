package io.searchable.core.application;

import io.searchable.core.domain.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Closes the remaining branches in {@link FacetFilter}. */
class FacetFilterBranchTest {

    private SearchHit hit(final String id, final String ns, final Map<String, Object> meta) {
        return new SearchHit(id, ns, "t", "c", 1.0, Map.of(), meta);
    }

    @Test
    void nullFilterMapPreservesAllHits() {
        final List<SearchHit> hits = List.of(hit("d", "ns", Map.of()));
        assertThat(FacetFilter.apply(hits, null)).hasSize(1);
    }

    @Test
    void filterOnMissingMetadataDropsHit() {
        final List<SearchHit> hits = List.of(hit("d", "ns", Map.of()));
        // Key absent -> lookup returns null -> hit filtered out
        assertThat(FacetFilter.apply(hits, Map.of("any", "value"))).isEmpty();
    }

    @Test
    void documentIdReservedKeyMatchesDocumentId() {
        final List<SearchHit> hits = List.of(
            hit("d1", "ns", Map.of()),
            hit("d2", "ns", Map.of()));
        final var filtered = FacetFilter.apply(hits,
            Map.of(FacetFilter.DOCUMENT_ID_KEY, "d2"));
        assertThat(filtered).extracting(SearchHit::documentId).containsExactly("d2");
    }

    @Test
    void listMetadataDoesNotMatchWhenNoElementOverlaps() {
        final List<SearchHit> hits = List.of(hit("d", "ns",
            Map.of("tags", List.of("a", "b"))));
        assertThat(FacetFilter.apply(hits, Map.of("tags", "z"))).isEmpty();
    }

    @Test
    void listMetadataIgnoresNullElements() {
        // Metadata stored as List with a null element should still match when
        // another element matches; null shouldn't break the scan.
        final Map<String, Object> meta = new HashMap<>();
        meta.put("tags", Arrays.asList(null, "match"));
        final List<SearchHit> hits = List.of(hit("d", "ns", meta));
        assertThat(FacetFilter.apply(hits, Map.of("tags", "match"))).hasSize(1);
    }

    // Note: the toStr(null) branch in FacetFilter cannot be reached at
    // runtime because matches() calls List.copyOf() on the filter value,
    // which rejects nulls before toStr ever runs.
}
