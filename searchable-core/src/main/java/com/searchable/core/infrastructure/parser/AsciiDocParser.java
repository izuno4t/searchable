package com.searchable.core.infrastructure.parser;

import com.searchable.core.domain.parser.DocumentParser;
import com.searchable.core.domain.parser.ParsedDocument;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lightweight AsciiDoc parser. Extracts a plain-text representation suitable
 * for indexing and uses the document title ({@code = Title}) when present.
 *
 * <p>This is intentionally regex-based; a richer AsciidoctorJ-based parser
 * can be swapped in via the {@link DocumentParser} SPI when needed.
 */
public final class AsciiDocParser implements DocumentParser {

    private static final Pattern DOC_TITLE = Pattern.compile("^=\\s+(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern HEADING_MARK = Pattern.compile("^=+\\s+", Pattern.MULTILINE);
    private static final Pattern ATTRIBUTE_LINE = Pattern.compile("^:[^:]+:.*$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK = Pattern.compile("----[\\s\\S]*?----");
    private static final Pattern INLINE_MONOSPACE = Pattern.compile("`([^`]+)`");
    private static final Pattern INLINE_EMPHASIS = Pattern.compile("\\*([^*]+)\\*");
    private static final Pattern INLINE_UNDERLINE = Pattern.compile("_([^_]+)_");
    private static final Pattern LINK = Pattern.compile("link:[^\\[]+\\[([^\\]]*)\\]");
    private static final Pattern XREF = Pattern.compile("<<[^,>]+,([^>]+)>>");
    private static final Pattern LIST_MARK = Pattern.compile("^[\\-*]\\s+|^\\.\\s+",
        Pattern.MULTILINE);
    private static final Pattern MULTI_BLANK = Pattern.compile("\\R{3,}");

    @Override
    public String name() {
        return "asciidoc";
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of(".adoc", ".asciidoc");
    }

    @Override
    public ParsedDocument parse(final String source, final String fallbackTitle) {
        Objects.requireNonNull(source, "source must not be null");
        final String title = extractTitle(source, fallbackTitle);
        final String content = stripAsciiDoc(source);
        return new ParsedDocument(title, content, Map.of("format", "asciidoc"));
    }

    private String extractTitle(final String source, final String fallbackTitle) {
        final var matcher = DOC_TITLE.matcher(source);
        if (matcher.find() && matcher.start() == 0) {
            return matcher.group(1).trim();
        }
        for (final String line : source.split("\\R", -1)) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
            }
        }
        return fallbackTitle == null || fallbackTitle.isBlank() ? "(untitled)" : fallbackTitle;
    }

    private String stripAsciiDoc(final String source) {
        String text = CODE_BLOCK.matcher(source).replaceAll("");
        text = ATTRIBUTE_LINE.matcher(text).replaceAll("");
        text = HEADING_MARK.matcher(text).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        text = XREF.matcher(text).replaceAll("$1");
        text = INLINE_MONOSPACE.matcher(text).replaceAll("$1");
        text = INLINE_EMPHASIS.matcher(text).replaceAll("$1");
        text = INLINE_UNDERLINE.matcher(text).replaceAll("$1");
        text = LIST_MARK.matcher(text).replaceAll("");
        text = MULTI_BLANK.matcher(text).replaceAll("\n\n");
        return text.trim();
    }
}
