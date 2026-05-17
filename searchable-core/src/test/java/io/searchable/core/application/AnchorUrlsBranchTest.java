package io.searchable.core.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorUrlsBranchTest {

    @Test
    void anchorForReturnsNullBaseWhenBlankHeading() {
        // baseUrl==null + blank heading -> slug.isEmpty() short-circuit
        assertThat(AnchorUrls.anchorFor(null, "")).isNull();
        assertThat(AnchorUrls.anchorFor(null, null)).isNull();
    }

    @Test
    void anchorForUsesEmptyBaseWhenBaseNull() {
        // baseUrl == null branch when slug is non-empty -> base="" + "#" + slug
        assertThat(AnchorUrls.anchorFor(null, "Section A")).isEqualTo("#section-a");
    }

    @Test
    void slugifyTrimsTrailingDashOnlyWhenPresent() {
        // The "sb ends with '-'" branch fires when the last char is non-slug.
        assertThat(AnchorUrls.slugify("hello!")).isEqualTo("hello");
        // No trailing dash trim path
        assertThat(AnchorUrls.slugify("hello")).isEqualTo("hello");
    }

    @Test
    void slugifyCollapsesAdjacentSeparatorsViaLastDashFlag() {
        // Multiple non-slug chars in a row -> only one dash inserted thanks
        // to the lastDash short-circuit.
        assertThat(AnchorUrls.slugify("foo!!!bar"))
            .isEqualTo("foo-bar");
        // Leading non-slug chars hit the "lastDash already true" branch
        assertThat(AnchorUrls.slugify("!!!foo")).isEqualTo("foo");
    }

    @Test
    void slugifyHandlesDiacritics() {
        assertThat(AnchorUrls.slugify("café"))
            .isEqualTo("cafe");
    }
}
