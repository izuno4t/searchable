package io.searchable.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single piece of context fed to an {@link AiProvider}, typically built
 * from a {@code SearchHit} or {@code SubResult}.
 *
 * @param sourceId  stable identifier for citation / deduplication
 *                  (usually a document or section id)
 * @param title     short title rendered to the user with the citation
 * @param text      content actually passed to the model
 * @param metadata  optional metadata (URL, namespace, score, ...) that
 *                  the prompt template may reference
 */
public record AiContextItem(String sourceId, String title, String text,
                            Map<String, Object> metadata) {

    public AiContextItem {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
