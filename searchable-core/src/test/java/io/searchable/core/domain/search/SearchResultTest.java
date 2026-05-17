package io.searchable.core.domain.search;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchResultTest {

    @Test
    void emptyFactoryYieldsZeroes() {
        final SearchResult r = SearchResult.empty(42L);
        assertThat(r.hits()).isEmpty();
        assertThat(r.totalHits()).isZero();
        assertThat(r.maxScore()).isZero();
        assertThat(r.tookMs()).isEqualTo(42L);
        assertThat(r.aggregations()).isEmpty();
    }

    @Test
    void rejectsNullHits() {
        assertThatThrownBy(() -> new SearchResult(null, 0L, 0, Map.of(), 0L))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeTotalOrTook() {
        assertThatThrownBy(() -> new SearchResult(List.of(), -1L, 0, Map.of(), 0L))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("totalHits");
        assertThatThrownBy(() -> new SearchResult(List.of(), 0L, 0, Map.of(), -5L))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tookMs");
    }

    @Test
    void hitsAreDefensivelyCopied() {
        final SearchHit hit = new SearchHit("d", "ns", "t", "c", 1.0,
            Map.of(), Map.of());
        final java.util.ArrayList<SearchHit> mutable = new java.util.ArrayList<>();
        mutable.add(hit);
        final SearchResult r = new SearchResult(mutable, 1, 1.0, null, 0L);
        mutable.clear();
        assertThat(r.hits()).hasSize(1);
        assertThat(r.aggregations()).isEmpty();
    }

    @Test
    void aggregationsAreDefensivelyCopied() {
        final Map<String, Object> agg = new HashMap<>();
        agg.put("k", 1);
        final SearchResult r = new SearchResult(List.of(), 0, 0, agg, 0L);
        agg.put("k", 99);
        agg.put("k2", 2);
        assertThat(r.aggregations()).containsOnlyKeys("k");
    }
}
