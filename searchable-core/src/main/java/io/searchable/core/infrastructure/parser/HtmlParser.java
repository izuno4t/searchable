package io.searchable.core.infrastructure.parser;

import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTML parser backed by jsoup.
 *
 * <p>Extracts the document {@code <title>} (or the first {@code <h1>} as
 * fallback) and strips all markup to produce plain text suitable for
 * full-text indexing.
 */
public final class HtmlParser implements DocumentParser {

    @Override
    public String name() {
        return "html";
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of(".html", ".htm", ".xhtml");
    }

    @Override
    public ParsedDocument parse(final String source, final String fallbackTitle) {
        Objects.requireNonNull(source, "source must not be null");
        final Document doc = Jsoup.parse(source);
        final String title = resolveTitle(doc, fallbackTitle);
        final String content = extractText(doc);
        return new ParsedDocument(title, content, buildMetadata(doc));
    }

    private String resolveTitle(final Document doc, final String fallbackTitle) {
        final String headTitle = doc.title();
        if (headTitle != null && !headTitle.isBlank()) {
            return headTitle.trim();
        }
        final Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }
        return fallbackTitle == null || fallbackTitle.isBlank() ? "(untitled)" : fallbackTitle;
    }

    private String extractText(final Document doc) {
        doc.select("script,style,noscript,template").remove();
        final Element body = doc.body();
        return (body == null ? doc : body).text();
    }

    private Map<String, Object> buildMetadata(final Document doc) {
        final Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("format", "html");
        final Map<String, String> meta = new LinkedHashMap<>();
        final Elements descriptions = doc.select("meta[name=description]");
        if (!descriptions.isEmpty()) {
            meta.put("description", descriptions.first().attr("content"));
        }
        final Element charset = doc.selectFirst("meta[charset]");
        if (charset != null) {
            meta.put("charset", charset.attr("charset"));
        }
        if (!meta.isEmpty()) {
            metadata.put("meta", meta);
        }
        return metadata;
    }
}
