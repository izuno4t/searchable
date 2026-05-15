package com.searchable.core.infrastructure.parser;

import com.searchable.core.domain.parser.ParsedDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlParserTest {

    @Test
    void extractsTitleFromHeadTag() {
        final String html = """
            <!doctype html>
            <html>
              <head><title>ドキュメントタイトル</title></head>
              <body>
                <h1>見出し</h1>
                <p>本文の <strong>強調</strong> 部分。</p>
                <script>console.log('skip');</script>
              </body>
            </html>
            """;
        final ParsedDocument p = new HtmlParser().parse(html, null);

        assertThat(p.title()).isEqualTo("ドキュメントタイトル");
        assertThat(p.content())
            .contains("見出し")
            .contains("本文")
            .contains("強調")
            .doesNotContain("console.log");
    }

    @Test
    void fallsBackToH1WhenTitleMissing() {
        final String html = "<html><body><h1>見出しのみ</h1><p>本文</p></body></html>";
        assertThat(new HtmlParser().parse(html, "fb").title()).isEqualTo("見出しのみ");
    }

    @Test
    void usesFallbackTitleWhenNeitherPresent() {
        assertThat(new HtmlParser().parse("<html><body><p>x</p></body></html>", "fallback").title())
            .isEqualTo("fallback");
    }

    @Test
    void capturesDescriptionMeta() {
        final String html = """
            <html><head>
              <title>t</title>
              <meta name="description" content="ページの説明">
            </head><body>本文</body></html>
            """;
        final ParsedDocument p = new HtmlParser().parse(html, null);
        final Object meta = p.metadata().get("meta");
        assertThat(meta).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        final java.util.Map<String, String> metaMap = (java.util.Map<String, String>) meta;
        assertThat(metaMap).containsEntry("description", "ページの説明");
    }

    @Test
    void registryDispatchesHtmlExtensions() {
        final ParserRegistry registry = ParserRegistry.defaults();
        assertThat(registry.resolveForFile("page.html")).map(p -> p.name()).contains("html");
        assertThat(registry.resolveForFile("page.htm")).map(p -> p.name()).contains("html");
    }
}
