package io.searchable.core.domain.search;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sub-result attached to a {@link SearchHit}.
 *
 * <p>A sub-result represents a section within a parent document that matched
 * the query independently. Search responses can present these so users can
 * jump straight to the most relevant section instead of opening the whole
 * document.
 *
 * @param sectionId        identifier unique within the parent document
 *                         (typically {@code parentId#section-ordinal})
 * @param parentDocumentId id of the {@link SearchHit#documentId() owning hit}
 * @param level            heading level (1 for {@code h1}, 2 for {@code h2}, ...);
 *                         {@code 0} when the section appears before any heading
 * @param heading          heading text (empty when {@code level == 0})
 * @param content          section body (already truncated when used as a snippet)
 * @param score            section-level relevance score
 * @param highlights       highlighted fragments per field; never {@code null}
 * @param anchorUrl        optional anchor URL produced from the section heading
 *                         (filled in by {@code TASK-051}); may be {@code null}
 */
public record SubResult(
    String sectionId,
    String parentDocumentId,
    int level,
    String heading,
    String content,
    double score,
    Map<String, List<String>> highlights,
    String anchorUrl
) {

    public SubResult {
        Objects.requireNonNull(sectionId, "sectionId must not be null");
        Objects.requireNonNull(parentDocumentId, "parentDocumentId must not be null");
        Objects.requireNonNull(heading, "heading must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (sectionId.isBlank()) {
            throw new IllegalArgumentException("sectionId must not be blank");
        }
        if (parentDocumentId.isBlank()) {
            throw new IllegalArgumentException("parentDocumentId must not be blank");
        }
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative");
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score must be finite, was " + score);
        }
        highlights = highlights == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(highlights));
    }
}
