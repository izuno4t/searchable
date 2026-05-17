package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.ParsedDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Closes the remaining title / structure branches in Markdown and AsciiDoc parsers. */
class MarkdownAndAsciiDocExtraTest {

    private final MarkdownParser md = new MarkdownParser();
    private final AsciiDocParser ad = new AsciiDocParser();

    @Test
    void markdownExposesIdentityMetadata() {
        assertThat(md.name()).isEqualTo("markdown");
        assertThat(md.supportedExtensions()).contains(".md", ".markdown");
    }

    @Test
    void markdownRejectsNullSource() {
        assertThatThrownBy(() -> md.parse((String) null, "f"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void markdownFallsBackToSetextTitleWhenAtxAbsent() {
        final String setext = "First Heading\n=====\n\nbody\n";
        final ParsedDocument p = md.parse(setext, "fb");
        assertThat(p.title()).isEqualTo("First Heading");
    }

    @Test
    void markdownFirstNonBlankLineAsTitleWhenNoHeading() {
        final String src = "\n\n  もっとも先頭の段落\nもう一行\n";
        final ParsedDocument p = md.parse(src, "fb");
        assertThat(p.title()).isEqualTo("もっとも先頭の段落");
    }

    @Test
    void markdownTruncatesOverLongFirstLine() {
        final String longLine = "x".repeat(300);
        final ParsedDocument p = md.parse(longLine, null);
        assertThat(p.title()).hasSize(200);
    }

    @Test
    void markdownUsesFallbackWhenSourceBlank() {
        final ParsedDocument p = md.parse("\n   \n  \n", "fname.md");
        assertThat(p.title()).isEqualTo("fname.md");
    }

    @Test
    void markdownUsesUntitledWhenSourceAndFallbackBlank() {
        final ParsedDocument p = md.parse("\n\n", "");
        assertThat(p.title()).isEqualTo("(untitled)");

        final ParsedDocument p2 = md.parse("\n\n", null);
        assertThat(p2.title()).isEqualTo("(untitled)");
    }

    @Test
    void markdownExtractsMultipleSections() {
        final String src = """
            # 章1
            本文1
            ## 章2
            本文2
            """;
        final ParsedDocument p = md.parse(src, null);
        assertThat(p.sections()).hasSize(2);
        assertThat(p.sections().get(0).level()).isEqualTo(1);
        assertThat(p.sections().get(1).level()).isEqualTo(2);
        assertThat(p.sections().get(0).heading()).isEqualTo("章1");
    }

    @Test
    void markdownStripsBlockquotesAndHorizontalRules() {
        final String src = "# t\n\n> 引用\n\n---\n\n本文\n";
        final ParsedDocument p = md.parse(src, null);
        assertThat(p.content()).contains("引用").contains("本文").doesNotContain(">");
    }

    @Test
    void asciidocExposesIdentityMetadata() {
        assertThat(ad.name()).isEqualTo("asciidoc");
        assertThat(ad.supportedExtensions()).contains(".adoc", ".asciidoc");
    }

    @Test
    void asciidocRejectsNullSource() {
        assertThatThrownBy(() -> ad.parse((String) null, "f"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void asciidocFallsBackToFirstNonBlankLine() {
        final ParsedDocument p = ad.parse("\n\n最初の非空行\nつぎ\n", "fb");
        assertThat(p.title()).isEqualTo("最初の非空行");
    }

    @Test
    void asciidocUsesFallbackWhenSourceBlank() {
        final ParsedDocument p = ad.parse("\n\n", "guide.adoc");
        assertThat(p.title()).isEqualTo("guide.adoc");
    }

    @Test
    void asciidocUsesUntitledWhenFallbackBlankOrNull() {
        assertThat(ad.parse("\n\n", "").title()).isEqualTo("(untitled)");
        assertThat(ad.parse("\n\n", null).title()).isEqualTo("(untitled)");
    }

    @Test
    void asciidocTruncatesOverLongFirstLine() {
        final String longLine = "y".repeat(300);
        final ParsedDocument p = ad.parse(longLine, null);
        assertThat(p.title()).hasSize(200);
    }

    @Test
    void asciidocStripsXrefAndLinks() {
        final String src = """
            = タイトル

            See link:https://e.x/[Example] and <<sec-1,section one>>.
            """;
        final ParsedDocument p = ad.parse(src, null);
        assertThat(p.content()).contains("Example").contains("section one");
        assertThat(p.content()).doesNotContain("https://e.x");
        assertThat(p.content()).doesNotContain("<<");
    }
}
