package io.searchable.core.domain.search;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Closes the branches not already covered by {@link SubResultTest}. */
class SubResultExtraTest {

    @Test
    void rejectsBlankParentDocumentId() {
        assertThatThrownBy(() -> new SubResult("id", " ", 1, "h", "c", 0, Map.of(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parentDocumentId");
    }

    @Test
    void rejectsInfiniteScore() {
        assertThatThrownBy(() -> new SubResult("id", "p", 1, "h", "c",
                Double.POSITIVE_INFINITY, Map.of(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullHighlightsBecomesEmptyMap() {
        final SubResult r = new SubResult("s", "p", 0, "", "c", 0, null, null);
        assertThat(r.highlights()).isEmpty();
    }

    @Test
    void highlightsAreDefensivelyCopied() {
        final Map<String, List<String>> h = new HashMap<>();
        h.put("content", List.of("frag"));
        final SubResult r = new SubResult("s", "p", 0, "", "c", 0, h, null);
        h.put("title", List.of("xxx"));
        assertThat(r.highlights()).containsOnlyKeys("content");
    }
}
