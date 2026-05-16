package io.searchable.core.application;

import io.searchable.core.domain.search.FacetSpec;
import io.searchable.core.domain.search.SearchHit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-search facet aggregator. Walks the hits returned by the search
 * engine and counts distinct facet values according to the supplied
 * {@link FacetSpec} list (TASK-054 / TASK-056).
 *
 * <p>The result is shaped as {@code { facetName: { value: count } }} and
 * intended for direct inclusion in
 * {@code SearchResult.aggregations}. Counts are computed over the
 * <em>returned</em> hits, not the full Lucene match set; UIs that need
 * exhaustive counts should raise the pagination limit before calling.
 */
public final class FacetAggregator {

    /** Reserved facet name applied to every namespace mentioned in the hits. */
    public static final String NAMESPACE_FACET = "_namespace";

    private FacetAggregator() { }

    public static Map<String, Map<String, Long>> aggregate(
            final List<SearchHit> hits, final List<FacetSpec> specs) {
        final Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        if (specs == null || specs.isEmpty()) {
            return out;
        }
        for (final FacetSpec spec : specs) {
            final Map<String, Long> counts = new LinkedHashMap<>();
            for (final SearchHit hit : hits) {
                for (final String value : valuesFor(spec, hit)) {
                    counts.merge(value, 1L, Long::sum);
                }
            }
            out.put(spec.name(), counts);
        }
        return out;
    }

    private static List<String> valuesFor(final FacetSpec spec, final SearchHit hit) {
        return switch (spec.mode()) {
            case INLINE -> inline(hit.metadata(), spec.field());
            case ATTRIBUTE -> attribute(hit.metadata(), spec.field());
            case CONTENT -> extractFromContent(hit.content(), spec.field());
        };
    }

    private static List<String> inline(final Map<String, Object> metadata, final String field) {
        if (NAMESPACE_FACET.equals(field)) {
            // Reserved facet handled separately by callers.
            return List.of();
        }
        final Object raw = metadata.get(field);
        return flatten(raw);
    }

    @SuppressWarnings("unchecked")
    private static List<String> attribute(final Map<String, Object> metadata, final String path) {
        Object cursor = metadata;
        for (final String segment : path.split("\\.")) {
            if (!(cursor instanceof Map<?, ?> m)) {
                return List.of();
            }
            cursor = ((Map<String, Object>) m).get(segment);
            if (cursor == null) {
                return List.of();
            }
        }
        return flatten(cursor);
    }

    private static List<String> extractFromContent(final String content, final String tagKey) {
        if (content == null || content.isBlank() || tagKey.isBlank()) {
            return List.of();
        }
        // Recognise inline markers like [key:value] anywhere in the body.
        final Pattern pattern = Pattern.compile(
            "\\[" + Pattern.quote(tagKey) + ":([^\\]]+)\\]");
        final Matcher matcher = pattern.matcher(content);
        final java.util.ArrayList<String> result = new java.util.ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1).trim());
        }
        return result;
    }

    private static List<String> flatten(final Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
        }
        return List.of(value.toString());
    }
}
