package io.searchable.core.domain.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Output of {@link DocumentParser#parse(String, String)}.
 *
 * @param title    extracted document title (never blank)
 * @param content  plain-text content suitable for indexing
 * @param metadata optional metadata extracted from the document
 * @param sections optional structural sections (heading + body) extracted
 *                 from the source. Empty when the parser cannot infer
 *                 structure (plain text, PDF, etc.)
 */
public record ParsedDocument(String title, String content,
                             Map<String, Object> metadata,
                             List<Section> sections) {

    public ParsedDocument {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    /** Backward-compatible constructor producing a document with no sections. */
    public ParsedDocument(final String title, final String content,
                          final Map<String, Object> metadata) {
        this(title, content, metadata, List.of());
    }

    /**
     * Structural unit extracted from the source document.
     *
     * @param level   heading level (1 = top-level), 0 for content before
     *                the first heading
     * @param heading heading text (may be empty for {@code level == 0})
     * @param content body text under this heading until the next heading
     *                of equal or higher level
     */
    public record Section(int level, String heading, String content) {

        public Section {
            Objects.requireNonNull(heading, "heading must not be null");
            Objects.requireNonNull(content, "content must not be null");
            if (level < 0) {
                throw new IllegalArgumentException("level must not be negative");
            }
        }
    }
}
