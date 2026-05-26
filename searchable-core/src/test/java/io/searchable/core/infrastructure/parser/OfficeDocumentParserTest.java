package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OfficeDocumentParser}. Fixtures for the formats POI can
 * write (.docx/.xlsx/.pptx/.xls/.ppt) are generated in-memory; the legacy
 * .doc (HWPF) format is read-only in POI so it is covered only at the
 * registration/MIME level (its extraction shares the same
 * {@code ExtractorFactory} code path exercised by .xls and .ppt).
 */
class OfficeDocumentParserTest {

    private byte[] buildDocx(final String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (final String text : paragraphs) {
                doc.createParagraph().createRun().setText(text);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildWorkbook(final Workbook wb) throws Exception {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final Sheet sheet = wb.createSheet("Report");
            final Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("Quarter");
            r0.createCell(1).setCellValue("Revenue");
            final Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Q1");
            r1.createCell(1).setCellValue("1000");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPptx(final String text) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final XSLFSlide slide = ppt.createSlide();
            final XSLFTextBox box = slide.createTextBox();
            box.setText(text);
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPpt(final String text) throws Exception {
        try (HSLFSlideShow ppt = new HSLFSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final HSLFSlide slide = ppt.createSlide();
            final HSLFTextBox box = slide.createTextBox();
            box.setText(text);
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private ParsedDocument parse(final String extension, final byte[] bytes) throws Exception {
        final DocumentParser parser = ParserRegistry.defaults()
            .resolveForExtension(extension)
            .orElseThrow();
        return parser.parse(new ByteArrayInputStream(bytes), "fallback");
    }

    @Test
    void extractsDocxText() throws Exception {
        final ParsedDocument p = parse(".docx", buildDocx("Word Title", "Hello Word World"));
        assertThat(p.title()).isEqualTo("Word Title");
        assertThat(p.content()).contains("Hello Word World");
        assertThat(p.metadata()).containsEntry("format", "word-docx");
    }

    @Test
    void extractsXlsxWithSheetNameAndCells() throws Exception {
        final ParsedDocument p = parse(".xlsx", buildWorkbook(new XSSFWorkbook()));
        assertThat(p.content())
            .contains("Report")   // sheet name included
            .contains("Quarter")
            .contains("Revenue")
            .contains("Q1")
            .contains("1000");
    }

    @Test
    void extractsXlsWithSheetNameAndCells() throws Exception {
        final ParsedDocument p = parse(".xls", buildWorkbook(new HSSFWorkbook()));
        assertThat(p.content())
            .contains("Report")
            .contains("Quarter")
            .contains("Q1");
    }

    @Test
    void extractsPptxText() throws Exception {
        final ParsedDocument p = parse(".pptx", buildPptx("Slide Heading"));
        assertThat(p.content()).contains("Slide Heading");
        assertThat(p.metadata()).containsEntry("format", "powerpoint-pptx");
    }

    @Test
    void extractsPptText() throws Exception {
        final ParsedDocument p = parse(".ppt", buildPpt("Legacy Slide"));
        assertThat(p.content()).contains("Legacy Slide");
    }

    @Test
    void parseStringIsUnsupported() {
        assertThatThrownBy(() ->
            new OfficeDocumentParser("word-docx", ".docx", "x").parse("not office", "fb"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registryDispatchesAllOfficeExtensionsWithCorrectMime() {
        final ParserRegistry registry = ParserRegistry.defaults();
        assertMime(registry, "report.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertMime(registry, "report.doc", "application/msword");
        assertMime(registry, "report.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertMime(registry, "report.xls", "application/vnd.ms-excel");
        assertMime(registry, "report.pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        assertMime(registry, "report.ppt", "application/vnd.ms-powerpoint");
    }

    private void assertMime(final ParserRegistry registry, final String fileName,
                            final String expectedMime) {
        assertThat(registry.resolveForFile(fileName))
            .as("parser for %s", fileName)
            .isPresent()
            .get()
            .extracting(DocumentParser::contentType)
            .isEqualTo(expectedMime);
    }
}
