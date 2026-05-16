package io.searchable.core.application;

import io.searchable.core.domain.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultMergerTest {

    private SearchHit hit(final String id, final double score) {
        return new SearchHit(id, "ns", "t", "c", score, Map.of(), Map.of());
    }

    @Test
    void rrfRanksDocumentsAppearingInBothListsHighest() {
        final ResultMerger merger = new ResultMerger();
        final List<SearchHit> ft = List.of(hit("a", 1.0), hit("b", 0.8), hit("c", 0.6));
        final List<SearchHit> vec = List.of(hit("b", 0.95), hit("a", 0.7), hit("d", 0.5));

        final List<SearchHit> merged = merger.reciprocalRankFusion(List.of(ft, vec), 60);

        assertThat(merged).extracting(SearchHit::documentId).containsSequence("a", "b");
        assertThat(merged.stream().map(SearchHit::documentId).toList())
            .contains("c").contains("d");
    }

    @Test
    void rrfUsesUnionOfAllInputs() {
        final ResultMerger merger = new ResultMerger();
        final List<SearchHit> ft = List.of(hit("a", 1.0));
        final List<SearchHit> vec = List.of(hit("b", 0.9));
        assertThat(merger.reciprocalRankFusion(List.of(ft, vec), 60))
            .extracting(SearchHit::documentId)
            .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void rrfRejectsNonPositiveK() {
        assertThatThrownBy(() ->
            new ResultMerger().reciprocalRankFusion(List.of(List.of(hit("a", 1))), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intersectKeepsOnlyDocumentsInBothLists() {
        final ResultMerger merger = new ResultMerger();
        final List<SearchHit> primary = List.of(hit("a", 1.0), hit("b", 0.8), hit("c", 0.6));
        final List<SearchHit> secondary = List.of(hit("b", 0.95), hit("a", 0.7));

        final List<SearchHit> merged = merger.intersect(primary, secondary);

        assertThat(merged).extracting(SearchHit::documentId).containsExactly("b", "a");
        assertThat(merged.get(0).score()).isEqualTo(0.95);
        assertThat(merged.get(1).score()).isEqualTo(0.7);
    }

    @Test
    void intersectReturnsEmptyWhenNoOverlap() {
        final ResultMerger merger = new ResultMerger();
        assertThat(merger.intersect(
            List.of(hit("a", 1.0)),
            List.of(hit("b", 0.9))
        )).isEmpty();
    }
}
