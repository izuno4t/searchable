package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.ParsedDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the remaining title-resolution branches in {@link PdfParser}. */
class PdfParserExtraTest {

    @Test
    void usesFirstNonBlankBodyLineWhenInfoTitleAbsent() throws Exception {
        final byte[] pdf = buildPdf(null, null, null, "Body Line One");
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), "fallback");

        assertThat(p.title()).isEqualTo("Body Line One");
        assertThat(p.metadata()).containsEntry("format", "pdf");
    }

    @Test
    void usesFallbackWhenInfoAndBodyEmpty() throws Exception {
        final byte[] pdf = buildEmptyPdf();
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), "fallback-name");
        assertThat(p.title()).isEqualTo("fallback-name");
    }

    @Test
    void usesUntitledWhenFallbackBlank() throws Exception {
        final byte[] pdf = buildEmptyPdf();
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), " ");
        assertThat(p.title()).isEqualTo("(untitled)");
    }

    @Test
    void usesUntitledWhenFallbackNull() throws Exception {
        final byte[] pdf = buildEmptyPdf();
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), null);
        assertThat(p.title()).isEqualTo("(untitled)");
    }

    @Test
    void capturesSubjectAndCreatorInPdfInfo() throws Exception {
        final byte[] pdf = buildPdf("T", "auth", "subj", "creator", "body");
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), null);

        @SuppressWarnings("unchecked")
        final Map<String, Object> info = (Map<String, Object>) p.metadata().get("pdfInfo");
        assertThat(info).containsEntry("subject", "subj").containsEntry("creator", "creator");
    }

    @Test
    void truncatesOverLongFirstLine() throws Exception {
        final String longLine = "x".repeat(500);
        final byte[] pdf = buildPdf(null, null, null, longLine);
        final ParsedDocument p = new PdfParser()
            .parse(new ByteArrayInputStream(pdf), null);
        assertThat(p.title()).hasSize(200);
    }

    private byte[] buildEmptyPdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdf(final String title, final String body) throws Exception {
        return buildPdf(title, null, null, body);
    }

    private byte[] buildPdf(final String title, final String author,
                             final String subject, final String body) throws Exception {
        return buildPdf(title, author, subject, null, body);
    }

    private byte[] buildPdf(final String title, final String author,
                             final String subject, final String creator,
                             final String body) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final PDDocumentInformation info = doc.getDocumentInformation();
            if (title != null) info.setTitle(title);
            if (author != null) info.setAuthor(author);
            if (subject != null) info.setSubject(subject);
            if (creator != null) info.setCreator(creator);

            final PDPage page = new PDPage();
            doc.addPage(page);
            if (body != null && !body.isBlank()) {
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText(body);
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
