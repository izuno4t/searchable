package com.searchable.core.infrastructure.parser;

import com.searchable.core.domain.parser.DocumentParser;
import com.searchable.core.domain.parser.ParsedDocument;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lightweight Markdown parser. Extracts a plain-text representation suitable
 * for indexing and uses the first level-1 heading (or first non-blank line)
 * as the title.
 *
 * <p>This is intentionally regex-based to avoid a full Markdown AST dependency;
 * accurate enough for full-text search needs in Phase 1.
 */
public final class MarkdownParser implements DocumentParser {

    private static final Pattern ATX_TITLE = Pattern.compile("^#\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern SETEXT_TITLE = Pattern.compile("^(.+)$\\R^=+\\s*$",
        Pattern.MULTILINE);
    private static final Pattern CODE_FENCE = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|_)(.+?)\\1");
    private static final Pattern HEADING_MARK = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    private static final Pattern BLOCKQUOTE_MARK = Pattern.compile("^>\\s?", Pattern.MULTILINE);
    private static final Pattern LIST_MARK = Pattern.compile("^[\\-*+]\\s+|^\\d+\\.\\s+",
        Pattern.MULTILINE);
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^[\\-*_]{3,}\\s*$",
        Pattern.MULTILINE);
    private static final Pattern MULTI_BLANK = Pattern.compile("\\R{3,}");

    @Override
    public String name() {
        return "markdown";
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of(".md", ".markdown");
    }

    @Override
    public ParsedDocument parse(final String source, final String fallbackTitle) {
        Objects.requireNonNull(source, "source must not be null");
        final String title = extractTitle(source, fallbackTitle);
        final String content = stripMarkdown(source);
        return new ParsedDocument(title, content, Map.of("format", "markdown"));
    }

    private String extractTitle(final String source, final String fallbackTitle) {
        for (final String line : source.split("\\R", -1)) {
            final var matcher = ATX_TITLE.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1).trim();
            }
        }
        final var setext = SETEXT_TITLE.matcher(source);
        if (setext.find()) {
            return setext.group(1).trim();
        }
        for (final String line : source.split("\\R", -1)) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
            }
        }
        return fallbackTitle == null || fallbackTitle.isBlank() ? "(untitled)" : fallbackTitle;
    }

    private String stripMarkdown(final String source) {
        String text = CODE_FENCE.matcher(source).replaceAll("");
        text = IMAGE.matcher(text).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = EMPHASIS.matcher(text).replaceAll("$2");
        text = HEADING_MARK.matcher(text).replaceAll("");
        text = BLOCKQUOTE_MARK.matcher(text).replaceAll("");
        text = LIST_MARK.matcher(text).replaceAll("");
        text = HORIZONTAL_RULE.matcher(text).replaceAll("");
        text = MULTI_BLANK.matcher(text).replaceAll("\n\n");
        return text.trim();
    }
}
