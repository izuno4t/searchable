package io.searchable.core.domain.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubResultTest {

    @Test
    void exposesAllAttributes() {
        final SubResult sr = new SubResult(
            "doc-1#section-2",
            "doc-1",
            2,
            "Architecture",
            "Layered architecture overview.",
            1.5,
            Map.of("content", List.of("<mark>architecture</mark>")),
            "/docs/doc-1#architecture");

        assertThat(sr.sectionId()).isEqualTo("doc-1#section-2");
        assertThat(sr.parentDocumentId()).isEqualTo("doc-1");
        assertThat(sr.level()).isEqualTo(2);
        assertThat(sr.heading()).isEqualTo("Architecture");
        assertThat(sr.content()).contains("Layered");
        assertThat(sr.score()).isEqualTo(1.5);
        assertThat(sr.highlights()).containsKey("content");
        assertThat(sr.anchorUrl()).endsWith("#architecture");
    }

    @Test
    void allowsNullAnchorUrl() {
        final SubResult sr = new SubResult(
            "id", "p", 0, "", "body", 0.1, Map.of(), null);
        assertThat(sr.anchorUrl()).isNull();
    }

    @Test
    void rejectsNegativeLevel() {
        assertThatThrownBy(() -> new SubResult(
            "id", "p", -1, "h", "c", 1.0, Map.of(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("level");
    }

    @Test
    void rejectsBlankSectionId() {
        assertThatThrownBy(() -> new SubResult(
            "", "p", 1, "h", "c", 1.0, Map.of(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sectionId");
    }

    @Test
    void rejectsNonFiniteScore() {
        assertThatThrownBy(() -> new SubResult(
            "id", "p", 1, "h", "c", Double.NaN, Map.of(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("score");
    }
}
