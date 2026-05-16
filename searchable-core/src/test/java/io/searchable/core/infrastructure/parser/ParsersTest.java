package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ParsersTest {

    @Test
    void plainTextUsesFirstLineAsTitle() {
        final ParsedDocument p = new PlainTextParser().parse(
            "ファイル先頭行\n2行目\n3行目", "fallback");
        assertThat(p.title()).isEqualTo("ファイル先頭行");
        assertThat(p.content()).contains("3行目");
    }

    @Test
    void plainTextUsesFallbackWhenSourceIsBlank() {
        final ParsedDocument p = new PlainTextParser().parse("   \n   \n", "my-file.txt");
        assertThat(p.title()).isEqualTo("my-file.txt");
    }

    @Test
    void markdownExtractsAtxTitleAndStripsMarkup() {
        final String source = """
            # ドキュメントタイトル

            ## 第1章

            本文には **強調** や *斜体*、`code` が混ざる。

            ```java
            class Foo {}
            ```

            - リスト1
            - リスト2
            """;
        final ParsedDocument p = new MarkdownParser().parse(source, null);
        assertThat(p.title()).isEqualTo("ドキュメントタイトル");
        assertThat(p.content())
            .contains("第1章")
            .contains("強調")
            .contains("斜体")
            .doesNotContain("```")
            .doesNotContain("**");
    }

    @Test
    void markdownStripsLinkUrlsKeepsLabel() {
        final ParsedDocument p = new MarkdownParser().parse(
            "# t\n\n[公式サイト](https://example.com) を参照\n", null);
        assertThat(p.content()).contains("公式サイト").doesNotContain("https://example.com");
    }

    @Test
    void asciidocExtractsTitleAndStripsMarkup() {
        final String source = """
            = ドキュメントタイトル
            :author: Alice

            == 第1章

            *強調* と _下線_ と `code` を含む段落。

            ----
            code block
            ----

            - リスト1
            - リスト2
            """;
        final ParsedDocument p = new AsciiDocParser().parse(source, null);
        assertThat(p.title()).isEqualTo("ドキュメントタイトル");
        assertThat(p.content())
            .contains("第1章")
            .contains("強調")
            .contains("下線")
            .doesNotContain("----")
            .doesNotContain(":author:");
    }

    @Test
    void registryDispatchesByExtension() {
        final ParserRegistry registry = ParserRegistry.defaults();
        final Optional<DocumentParser> md = registry.resolveForFile("README.md");
        final Optional<DocumentParser> adoc = registry.resolveForFile("guide.adoc");
        final Optional<DocumentParser> txt = registry.resolveForFile("notes.TXT");

        assertThat(md).map(DocumentParser::name).contains("markdown");
        assertThat(adoc).map(DocumentParser::name).contains("asciidoc");
        assertThat(txt).map(DocumentParser::name).contains("plain");
    }

    @Test
    void registryReturnsEmptyForUnknownExtension() {
        assertThat(ParserRegistry.defaults().resolveForFile("data.csv")).isEmpty();
    }
}
