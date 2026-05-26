package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Microsoft Office parser backed by Apache POI.
 *
 * <p>A single configurable parser covers every Office family because text
 * extraction is uniform: {@link ExtractorFactory#createExtractor(InputStream)}
 * auto-detects the concrete format (modern OOXML or legacy OLE2) and returns a
 * {@link POITextExtractor} whose {@link POITextExtractor#getText()} yields a
 * plain-text rendering suitable for full-text indexing. For spreadsheets POI's
 * Excel extractors include sheet names and emit one line per row with
 * tab-separated cell values.
 *
 * <p>Each registered instance is bound to one extension and its matching MIME
 * type (see {@code docs/architecture.md} §5.7), so {@link #contentType()} can
 * return a single accurate value per the {@code metadata.contentType} reserved
 * key contract.
 */
public final class OfficeDocumentParser implements DocumentParser {

    private final String name;
    private final List<String> extensions;
    private final String contentType;

    /**
     * @param name        short diagnostic identifier (e.g. {@code word-docx})
     * @param extension   single file extension, lowercase with leading dot
     * @param contentType MIME type produced for this extension
     */
    public OfficeDocumentParser(final String name,
                                final String extension,
                                final String contentType) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.extensions = List.of(Objects.requireNonNull(extension, "extension must not be null"));
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<String> supportedExtensions() {
        return extensions;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public ParsedDocument parse(final String source, final String fallbackTitle) {
        throw new UnsupportedOperationException(
            "Office parser requires the binary parse(InputStream, ...) overload");
    }

    @Override
    public ParsedDocument parse(final InputStream stream, final String fallbackTitle)
            throws IOException {
        final byte[] bytes;
        try (InputStream in = stream) {
            bytes = in.readAllBytes();
        }
        // ExtractorFactory inspects the file magic, so the stream must support
        // mark/reset; a ByteArrayInputStream does. Documents are loaded fully
        // into memory for indexing anyway.
        try (POITextExtractor extractor =
                 ExtractorFactory.createExtractor(new ByteArrayInputStream(bytes))) {
            final String content = extractor.getText().trim();

            final Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("format", name);

            return new ParsedDocument(resolveTitle(content, fallbackTitle), content, metadata);
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException("Failed to parse Office document (" + name + ")", e);
        }
    }

    private String resolveTitle(final String content, final String fallbackTitle) {
        for (final String line : content.split("\\R", -1)) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
            }
        }
        return fallbackTitle == null || fallbackTitle.isBlank() ? "(untitled)" : fallbackTitle;
    }
}
