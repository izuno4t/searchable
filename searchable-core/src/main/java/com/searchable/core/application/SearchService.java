package com.searchable.core.application;

import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.namespace.NamespaceRepository;
import com.searchable.core.domain.search.PaginationParams;
import com.searchable.core.domain.search.SearchHit;
import com.searchable.core.domain.search.SearchRequest;
import com.searchable.core.domain.search.SearchResult;
import com.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Application-layer search facade.
 *
 * <p>For Phase 1, full-text only. Vector and hybrid search will be added in
 * Phase 2 by composing additional searchers behind this interface.
 */
public final class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final NamespaceRepository namespaces;
    private final LuceneFullTextSearcher fullTextSearcher;

    public SearchService(final NamespaceRepository namespaces,
                         final LuceneFullTextSearcher fullTextSearcher) {
        this.namespaces = Objects.requireNonNull(namespaces);
        this.fullTextSearcher = Objects.requireNonNull(fullTextSearcher);
    }

    public SearchResult search(final SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        final long start = System.nanoTime();
        final List<String> targets = resolveTargets(request);
        if (targets.isEmpty()) {
            return SearchResult.empty(elapsedMs(start));
        }
        if (targets.size() == 1) {
            return fullTextSearcher.search(targets.get(0), request);
        }

        final List<SearchHit> all = new ArrayList<>();
        long totalHits = 0L;
        final PaginationParams pageAll = new PaginationParams(0,
            request.pagination().offset() + request.pagination().limit());
        final SearchRequest perNs = SearchRequest.builder()
            .query(request.query())
            .namespaceIds(List.of())
            .searchType(request.searchType())
            .options(request.options())
            .pagination(pageAll)
            .filters(request.filters())
            .build();

        for (final String namespaceId : targets) {
            final SearchResult partial = fullTextSearcher.search(namespaceId, perNs);
            totalHits += partial.totalHits();
            all.addAll(partial.hits());
        }

        all.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        final int from = Math.min(request.pagination().offset(), all.size());
        final int to = Math.min(from + request.pagination().limit(), all.size());
        final List<SearchHit> page = all.subList(from, to);
        final double maxScore = all.isEmpty() ? 0.0 : all.get(0).score();
        return new SearchResult(page, totalHits, maxScore, Map.of(), elapsedMs(start));
    }

    private List<String> resolveTargets(final SearchRequest request) {
        if (request.namespaceIds().isEmpty()) {
            return namespaces.findAll().stream().map(Namespace::id).toList();
        }
        for (final String id : request.namespaceIds()) {
            if (!namespaces.exists(id)) {
                throw new NoSuchElementException("Namespace not found: " + id);
            }
        }
        return request.namespaceIds();
    }

    private long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
