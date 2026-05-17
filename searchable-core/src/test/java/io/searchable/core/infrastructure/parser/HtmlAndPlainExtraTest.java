package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlAndPlainExtraTest {

    @Test
    void plainTextRejectsNullSource() {
        assertThatThrownBy(() -> new PlainTextParser().parse((String) null, "fb"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void plainTextTruncatesOverLongFirstLine() {
        final String longLine = "x".repeat(300);
        final ParsedDocument p = new PlainTextParser().parse(longLine, null);
        assertThat(p.title()).hasSize(200);
    }

    @Test
    void plainTextReturnsUntitledWhenSourceAndFallbackBlank() {
        assertThat(new PlainTextParser().parse("   ", "").title()).isEqualTo("(untitled)");
        assertThat(new PlainTextParser().parse("   ", null).title()).isEqualTo("(untitled)");
    }

    @Test
    void plainTextIdentity() {
        final PlainTextParser p = new PlainTextParser();
        assertThat(p.name()).isEqualTo("plain");
        assertThat(p.supportedExtensions()).contains(".txt", ".text", ".log");
    }

    @Test
    void htmlRejectsNullSource() {
        assertThatThrownBy(() -> new HtmlParser().parse((String) null, "fb"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void htmlUsesUntitledWhenAllBlank() {
        final ParsedDocument p = new HtmlParser().parse(
            "<html><body><p>x</p></body></html>", null);
        assertThat(p.title()).isEqualTo("(untitled)");
        final ParsedDocument p2 = new HtmlParser().parse(
            "<html><body><p>x</p></body></html>", " ");
        assertThat(p2.title()).isEqualTo("(untitled)");
    }

    @Test
    void htmlCapturesCharsetMeta() {
        final String html = """
            <html><head><meta charset="utf-8"><title>t</title></head><body>本文</body></html>
            """;
        final ParsedDocument p = new HtmlParser().parse(html, null);
        @SuppressWarnings("unchecked")
        final Map<String, String> meta = (Map<String, String>) p.metadata().get("meta");
        assertThat(meta).containsEntry("charset", "utf-8");
    }

    @Test
    void htmlBodylessUsesDocumentText() {
        // jsoup auto-creates a body; we still exercise the fallback branch by
        // using a document with empty body element so the body-null check is
        // covered by other tests; here we just assert it doesn't blow up.
        final ParsedDocument p = new HtmlParser().parse("<p>only paragraph</p>", "fb");
        assertThat(p.content()).contains("only paragraph");
    }
}
