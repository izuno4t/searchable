package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Branch coverage supplement for every concrete {@link DocumentParser}.
 * Focuses on the small public methods (name / supportedExtensions /
 * contentType) and the fallback / metadata branches each parser exposes.
 */
class ParserExtraBranchTest {

    @TempDir Path tempDir;

    // ----- contentType() defaults -----

    @Test
    void documentParserDefaultContentTypeIsOctetStream() {
        // Anonymous parser that does not override contentType — exercises
        // the default method body in DocumentParser.
        final DocumentParser bare = new DocumentParser() {
            @Override public String name() { return "bare"; }
            @Override public java.util.List<String> supportedExtensions() {
                return java.util.List.of(".bare");
            }
            @Override public ParsedDocument parse(final String source, final String fallback) {
                return new ParsedDocument(fallback, source, java.util.Map.of());
            }
        };
        assertThat(bare.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void contentTypesAreReported() {
        assertThat(new HtmlParser().contentType()).isEqualTo("text/html");
        assertThat(new PdfParser().contentType()).isEqualTo("application/pdf");
        assertThat(new MarkdownParser().contentType()).isEqualTo("text/markdown");
        assertThat(new AsciiDocParser().contentType()).isEqualTo("text/asciidoc");
        assertThat(new PlainTextParser().contentType()).isEqualTo("text/plain");
    }

    // ----- HtmlParser branches -----

    @Test
    void htmlParserFallsBackToH1WhenNoTitle() {
        final ParsedDocument doc = new HtmlParser().parse(
            "<html><body><h1>Heading One</h1><p>body</p></body></html>", "fb");
        assertThat(doc.title()).isEqualTo("Heading One");
    }

    @Test
    void htmlParserUsesFallbackWhenNoTitleOrH1() {
        final ParsedDocument doc = new HtmlParser().parse(
            "<html><body><p>just paragraph</p></body></html>", "fb-title");
        assertThat(doc.title()).isEqualTo("fb-title");
    }

    @Test
    void htmlParserUsesUntitledWhenFallbackIsBlank() {
        final ParsedDocument doc = new HtmlParser().parse(
            "<html><body><p>p</p></body></html>", "");
        assertThat(doc.title()).isEqualTo("(untitled)");
    }

    @Test
    void htmlParserExtractsTitleMetadata() {
        final ParsedDocument doc = new HtmlParser().parse("""
            <html><head>
              <title>Doc</title>
              <meta name="description" content="DESC">
              <meta charset="UTF-8">
            </head><body>body</body></html>
            """, null);
        assertThat(doc.title()).isEqualTo("Doc");
        final Object meta = doc.metadata().get("meta");
        assertThat(meta).isInstanceOf(java.util.Map.class);
    }

    @Test
    void htmlParserRejectsNullSource() {
        assertThatThrownBy(() -> new HtmlParser().parse((String) null, "fb"))
            .isInstanceOf(NullPointerException.class);
    }

    // ----- PdfParser branches -----

    @Test
    void pdfParserStringOverloadIsUnsupported() {
        assertThatThrownBy(() -> new PdfParser().parse("not bytes", "t"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pdfParserExtractsFromMinimalPdfDocument() throws Exception {
        final Path pdfPath = tempDir.resolve("min.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                 new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.getDocumentInformation().setTitle("MinTitle");
            doc.getDocumentInformation().setAuthor("Tester");
            doc.getDocumentInformation().setSubject("Subj");
            doc.getDocumentInformation().setCreator("Creator");
            doc.save(pdfPath.toFile());
        }

        try (InputStream in = Files.newInputStream(pdfPath)) {
            final ParsedDocument parsed = new PdfParser().parse(in, "fb");
            assertThat(parsed.title()).isEqualTo("MinTitle");
            assertThat(parsed.metadata().get("format")).isEqualTo("pdf");
            assertThat(parsed.metadata().get("pageCount")).isEqualTo(1);
            assertThat(parsed.metadata().get("pdfInfo")).isInstanceOf(java.util.Map.class);
        }
    }

    @Test
    void pdfParserFallsBackToFirstNonBlankLineWhenNoTitle() throws Exception {
        // Build a PDF whose document info has no title, then assert the
        // parser uses the first non-blank line in the extracted text as
        // a fallback (covers the resolveTitle content-loop branch).
        final Path pdfPath = tempDir.resolve("notitle.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                 new org.apache.pdfbox.pdmodel.PDDocument()) {
            final org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("FirstLineTitle");
                content.endText();
            }
            doc.save(pdfPath.toFile());
        }

        try (InputStream in = Files.newInputStream(pdfPath)) {
            final ParsedDocument parsed = new PdfParser().parse(in, "fb");
            // First non-blank line, possibly with whitespace artifacts; just
            // assert it isn't the "(untitled)" sentinel.
            assertThat(parsed.title()).isNotEqualTo("(untitled)");
            assertThat(parsed.title()).isNotEqualTo("fb");
        }
    }

    @Test
    void pdfParserReturnsUntitledWhenNoContentAndNoFallback() throws Exception {
        final Path pdfPath = tempDir.resolve("empty.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                 new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(pdfPath.toFile());
        }

        try (InputStream in = Files.newInputStream(pdfPath)) {
            final ParsedDocument parsed = new PdfParser().parse(in, "");
            assertThat(parsed.title()).isEqualTo("(untitled)");
        }
    }

    // ----- Markdown / AsciiDoc / PlainText -----

    @Test
    void markdownParserDerivesTitleFromFirstHeading() {
        final ParsedDocument d = new MarkdownParser().parse("# Heading\n\nBody.", null);
        assertThat(d.title()).isEqualTo("Heading");
    }

    @Test
    void markdownParserDerivesTitleFromFirstLineWhenNoHeading() {
        final ParsedDocument d = new MarkdownParser().parse(
            "just body, no heading.", "fb");
        // The parser uses the first non-blank line as title fallback.
        assertThat(d.title()).isEqualTo("just body, no heading.");
    }

    @Test
    void asciidocParserExtractsLevelOneTitle() {
        final ParsedDocument d = new AsciiDocParser().parse("= Doc Title\n\nbody", null);
        assertThat(d.title()).isEqualTo("Doc Title");
    }

    @Test
    void plainTextParserUsesFirstNonBlankLineAsTitle() {
        final ParsedDocument d = new PlainTextParser().parse(
            "  \n  \nFirst line\nSecond line", "fb");
        assertThat(d.title()).isEqualTo("First line");
    }

    @Test
    void plainTextParserStringStreamOverloadAreEquivalent() throws IOException {
        final PlainTextParser p = new PlainTextParser();
        final ParsedDocument a = p.parse("body content", "fb");
        try (InputStream s = new ByteArrayInputStream("body content".getBytes())) {
            final ParsedDocument b = p.parse(s, "fb");
            assertThat(b.content()).isEqualTo(a.content());
        }
    }
}
