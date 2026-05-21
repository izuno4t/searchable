package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF parser backed by Apache PDFBox.
 *
 * <p>Reads the document title from the PDF metadata (falling back to the
 * supplied {@code fallbackTitle} or the first non-blank line of extracted
 * text) and uses {@link PDFTextStripper} to extract a plain-text
 * representation suitable for full-text indexing.
 */
public final class PdfParser implements DocumentParser {

    @Override
    public String name() {
        return "pdf";
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of(".pdf");
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public ParsedDocument parse(final String source, final String fallbackTitle) {
        throw new UnsupportedOperationException(
            "PDF parser requires the binary parse(InputStream, ...) overload");
    }

    @Override
    public ParsedDocument parse(final InputStream stream, final String fallbackTitle)
            throws IOException {
        try (InputStream in = stream;
             PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(in))) {

            final PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            final String content = stripper.getText(document).trim();

            final Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("format", "pdf");
            metadata.put("pageCount", document.getNumberOfPages());

            final String title = resolveTitle(document.getDocumentInformation(),
                content, fallbackTitle, metadata);

            return new ParsedDocument(title, content, metadata);
        }
    }

    private String resolveTitle(final PDDocumentInformation info,
                                final String content,
                                final String fallbackTitle,
                                final Map<String, Object> metadata) {
        if (info != null) {
            final String docTitle = info.getTitle();
            if (docTitle != null && !docTitle.isBlank()) {
                addInfo(metadata, info);
                return docTitle.trim();
            }
            addInfo(metadata, info);
        }
        for (final String line : content.split("\\R", -1)) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
            }
        }
        return fallbackTitle == null || fallbackTitle.isBlank() ? "(untitled)" : fallbackTitle;
    }

    private void addInfo(final Map<String, Object> metadata, final PDDocumentInformation info) {
        final Map<String, Object> nested = new HashMap<>();
        if (info.getAuthor() != null) {
            nested.put("author", info.getAuthor());
        }
        if (info.getSubject() != null) {
            nested.put("subject", info.getSubject());
        }
        if (info.getCreator() != null) {
            nested.put("creator", info.getCreator());
        }
        if (!nested.isEmpty()) {
            metadata.put("pdfInfo", nested);
        }
    }
}
