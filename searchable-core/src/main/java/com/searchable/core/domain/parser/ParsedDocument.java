package com.searchable.core.domain.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Output of {@link DocumentParser#parse(String, String)}.
 *
 * @param title    extracted document title (never blank)
 * @param content  plain-text content suitable for indexing
 * @param metadata optional metadata extracted from the document
 */
public record ParsedDocument(String title, String content, Map<String, Object> metadata) {

    public ParsedDocument {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
