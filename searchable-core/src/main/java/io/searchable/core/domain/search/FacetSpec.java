package io.searchable.core.domain.search;

import java.util.Objects;

/**
 * Specification of a single facet to aggregate over a search result.
 *
 * <p>Three sourcing modes are supported (TASK-056):
 * <ul>
 *   <li>{@link Mode#INLINE} -- the value lives directly under
 *       {@code metadata[field]} (e.g. {@code metadata.category = "blog"}).</li>
 *   <li>{@link Mode#ATTRIBUTE} -- the value lives in a nested map at
 *       {@code metadata[path]} where {@code path} uses dot notation
 *       (e.g. {@code attributes.author.name}).</li>
 *   <li>{@link Mode#CONTENT} -- the value is parsed out of the
 *       document content via a simple key/value tag (e.g.
 *       {@code [author:佐藤]} embedded in markdown).</li>
 * </ul>
 *
 * @param name  facet name returned to the caller (used as the key in the
 *              aggregation map)
 * @param field source field / key / regex depending on {@link #mode()}
 * @param mode  how to extract the value from a hit
 */
public record FacetSpec(String name, String field, Mode mode) {

    public FacetSpec {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
    }

    public static FacetSpec inline(final String name) {
        return new FacetSpec(name, name, Mode.INLINE);
    }

    public static FacetSpec attribute(final String name, final String path) {
        return new FacetSpec(name, path, Mode.ATTRIBUTE);
    }

    public static FacetSpec content(final String name, final String tagKey) {
        return new FacetSpec(name, tagKey, Mode.CONTENT);
    }

    public enum Mode { INLINE, ATTRIBUTE, CONTENT }
}
