package io.searchable.core.application;

import io.searchable.core.domain.search.SearchHit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combines ranked hit lists from multiple search engines into a single
 * ranked list.
 *
 * <p>Two strategies are provided:
 * <ul>
 *   <li>{@link #reciprocalRankFusion} for parallel hybrid search
 *       (union with rank-based scoring)</li>
 *   <li>{@link #intersect} for sequential hybrid search (a document must
 *       appear in both lists; the secondary list's score wins)</li>
 * </ul>
 */
public final class ResultMerger {

    /** Default {@code k} constant for RRF. */
    public static final int DEFAULT_RRF_K = 60;

    /**
     * Reciprocal Rank Fusion: union of all input lists, scored by
     * {@code 1 / (k + rank)} summed across lists where the document appears.
     */
    public List<SearchHit> reciprocalRankFusion(final List<List<SearchHit>> rankedLists,
                                                final int rrfK) {
        Objects.requireNonNull(rankedLists, "rankedLists must not be null");
        if (rrfK <= 0) {
            throw new IllegalArgumentException("rrfK must be positive");
        }

        final Map<String, SearchHit> hits = new LinkedHashMap<>();
        final Map<String, Double> scores = new LinkedHashMap<>();

        for (final List<SearchHit> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                final SearchHit hit = list.get(rank);
                hits.putIfAbsent(hit.documentId(), hit);
                final double increment = 1.0 / (rrfK + rank + 1.0);
                scores.merge(hit.documentId(), increment, Double::sum);
            }
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> withScore(hits.get(e.getKey()), e.getValue()))
            .toList();
    }

    /**
     * Intersection: keep documents that appear in {@code primary}, using
     * the score of the matching entry in {@code secondary}. Documents
     * only in one list are discarded.
     */
    public List<SearchHit> intersect(final List<SearchHit> primary,
                                     final List<SearchHit> secondary) {
        Objects.requireNonNull(primary, "primary must not be null");
        Objects.requireNonNull(secondary, "secondary must not be null");

        final Map<String, SearchHit> secondaryById = new LinkedHashMap<>();
        for (final SearchHit hit : secondary) {
            secondaryById.put(hit.documentId(), hit);
        }

        final List<SearchHit> merged = new ArrayList<>();
        for (final SearchHit hit : primary) {
            final SearchHit reranked = secondaryById.get(hit.documentId());
            if (reranked != null) {
                merged.add(reranked);
            }
        }
        merged.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return merged;
    }

    private SearchHit withScore(final SearchHit hit, final double score) {
        return new SearchHit(
            hit.documentId(),
            hit.namespaceId(),
            hit.title(),
            hit.content(),
            score,
            hit.highlights(),
            hit.metadata()
        );
    }
}
