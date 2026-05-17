package io.searchable.core.domain.search;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchHitTest {

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new SearchHit(null, "ns", "t", "c", 0,
            Map.of(), Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchHit("d", null, "t", "c", 0,
            Map.of(), Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchHit("d", "ns", null, "c", 0,
            Map.of(), Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullCollectionsBecomeEmpty() {
        final SearchHit hit = new SearchHit("d", "ns", "t", "c", 0.0, null, null, null);
        assertThat(hit.highlights()).isEmpty();
        assertThat(hit.metadata()).isEmpty();
        assertThat(hit.subResults()).isEmpty();
    }

    @Test
    void backwardCompatibleConstructorYieldsEmptySubResults() {
        final SearchHit hit = new SearchHit("d", "ns", "t", "c", 0.0,
            Map.of("k", List.of("h")), Map.of("a", 1));
        assertThat(hit.subResults()).isEmpty();
        assertThat(hit.highlights().get("k")).containsExactly("h");
    }

    @Test
    void metadataIsDefensivelyCopied() {
        final Map<String, Object> meta = new HashMap<>();
        meta.put("a", 1);
        final SearchHit hit = new SearchHit("d", "ns", "t", "c", 0.0, Map.of(), meta);
        meta.put("a", 99);
        meta.put("b", 2);
        assertThat(hit.metadata()).containsOnlyKeys("a");
    }
}
