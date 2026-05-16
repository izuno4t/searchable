package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.ParsedDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfParserTest {

    @TempDir Path tempDir;

    private byte[] buildPdf(final String title, final String body) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(title);
            info.setAuthor("test-author");

            final PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(body);
                stream.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsTitleAndBody() throws Exception {
        final byte[] pdf = buildPdf("Doc Title", "Hello PDF World");
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), "fallback");

        assertThat(p.title()).isEqualTo("Doc Title");
        assertThat(p.content()).contains("Hello PDF World");
        assertThat(p.metadata()).containsKey("pageCount").containsEntry("format", "pdf");
    }

    @Test
    void capturesPdfMetadata() throws Exception {
        final byte[] pdf = buildPdf("Title", "Body");
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), null);

        @SuppressWarnings("unchecked")
        final java.util.Map<String, Object> info =
            (java.util.Map<String, Object>) p.metadata().get("pdfInfo");
        assertThat(info).containsEntry("author", "test-author");
    }

    @Test
    void parseStringIsUnsupported() {
        assertThatThrownBy(() -> new PdfParser().parse("not a pdf", "fb"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registryDispatchesPdf() {
        assertThat(ParserRegistry.defaults().resolveForFile("manual.pdf"))
            .map(p -> p.name()).contains("pdf");
    }
}
