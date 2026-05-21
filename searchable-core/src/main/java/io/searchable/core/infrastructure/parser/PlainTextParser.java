package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parser for plain-text documents.
 *
 * <p>The first non-blank line is used as the title when {@code fallbackTitle}
 * would otherwise be unhelpful (e.g. empty).
 */
public final class PlainTextParser implements DocumentParser {

    @Override
    public String name() {
        return "plain";
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of(".txt", ".text", ".log");
    }

    @Override
    public String contentType() {
        return "text/plain";
    }

    @Override
    public ParsedDocument parse(final String source, final String fallbackTitle) {
        Objects.requireNonNull(source, "source must not be null");
        final String title = extractTitle(source, fallbackTitle);
        return new ParsedDocument(title, source, Map.of("format", "plain"));
    }

    private String extractTitle(final String source, final String fallbackTitle) {
        for (final String line : source.split("\\R", -1)) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return truncate(trimmed);
            }
        }
        if (fallbackTitle != null && !fallbackTitle.isBlank()) {
            return fallbackTitle;
        }
        return "(untitled)";
    }

    private String truncate(final String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
