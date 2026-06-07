package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dispatches to the appropriate {@link DocumentParser} based on file extension.
 *
 * <p>Built-in parsers (Plain Text, Markdown, AsciiDoc, HTML, PDF, and Office
 * Word/Excel/PowerPoint) are registered by {@link #defaults()}; additional
 * parsers can be added via {@link #register}.
 */
public final class ParserRegistry {

    private final Map<String, DocumentParser> byExtension = new HashMap<>();

    /** Registry pre-populated with the parsers shipped by the library. */
    public static ParserRegistry defaults() {
        final ParserRegistry registry = new ParserRegistry();
        registry.register(new PlainTextParser());
        registry.register(new MarkdownParser());
        registry.register(new AsciiDocParser());
        registry.register(new HtmlParser());
        registry.register(new PdfParser());
        // Office formats (docs/devel/design/architecture/overview.md §5.7). Each extension maps to a
        // distinct MIME type, so one configurable parser is registered per
        // extension rather than per family.
        registry.register(new OfficeDocumentParser("word-docx", ".docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        registry.register(new OfficeDocumentParser("word-doc", ".doc",
            "application/msword"));
        registry.register(new OfficeDocumentParser("excel-xlsx", ".xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        registry.register(new OfficeDocumentParser("excel-xls", ".xls",
            "application/vnd.ms-excel"));
        registry.register(new OfficeDocumentParser("powerpoint-pptx", ".pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        registry.register(new OfficeDocumentParser("powerpoint-ppt", ".ppt",
            "application/vnd.ms-powerpoint"));
        return registry;
    }

    /** Register a parser for each of its {@link DocumentParser#supportedExtensions()}. */
    public ParserRegistry register(final DocumentParser parser) {
        Objects.requireNonNull(parser, "parser must not be null");
        for (final String ext : parser.supportedExtensions()) {
            byExtension.put(ext.toLowerCase(Locale.ROOT), parser);
        }
        return this;
    }

    /** Resolve the parser for the given file name (by its extension). */
    public Optional<DocumentParser> resolveForFile(final String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        final int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(byExtension.get(
            fileName.substring(dot).toLowerCase(Locale.ROOT)));
    }

    /** Resolve a parser for an explicit extension (with leading dot). */
    public Optional<DocumentParser> resolveForExtension(final String extension) {
        Objects.requireNonNull(extension, "extension must not be null");
        return Optional.ofNullable(byExtension.get(extension.toLowerCase(Locale.ROOT)));
    }

    public List<String> registeredExtensions() {
        return byExtension.keySet().stream().sorted().toList();
    }
}
