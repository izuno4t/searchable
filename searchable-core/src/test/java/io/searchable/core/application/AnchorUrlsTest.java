package io.searchable.core.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorUrlsTest {

    @Test
    void slugifyConvertsLatinHeadingsToKebabCase() {
        assertThat(AnchorUrls.slugify("Installation Guide")).isEqualTo("installation-guide");
        assertThat(AnchorUrls.slugify("Getting Started!")).isEqualTo("getting-started");
    }

    @Test
    void slugifyPreservesJapaneseCharacters() {
        assertThat(AnchorUrls.slugify("インストール手順")).isEqualTo("インストール手順");
        assertThat(AnchorUrls.slugify("使い方 Guide")).isEqualTo("使い方-guide");
    }

    @Test
    void slugifyHandlesBlankInput() {
        assertThat(AnchorUrls.slugify("")).isEmpty();
        assertThat(AnchorUrls.slugify("   ")).isEmpty();
        assertThat(AnchorUrls.slugify(null)).isEmpty();
    }

    @Test
    void anchorForCombinesBaseAndSlug() {
        assertThat(AnchorUrls.anchorFor("/docs/foo", "Architecture"))
            .isEqualTo("/docs/foo#architecture");
        assertThat(AnchorUrls.anchorFor("", "Section 1"))
            .isEqualTo("#section-1");
    }

    @Test
    void anchorForReturnsBaseWhenHeadingIsBlank() {
        assertThat(AnchorUrls.anchorFor("/docs/foo", "")).isEqualTo("/docs/foo");
        assertThat(AnchorUrls.anchorFor("/docs/foo", null)).isEqualTo("/docs/foo");
    }
}
