package io.searchable.core.application;

import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.domain.namespace.NamespaceRepository;
import io.searchable.core.domain.search.PaginationParams;
import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import io.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import io.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Application-layer search facade.
 *
 * <p>The effective {@link SearchType} is resolved per request:
 * <ol>
 *   <li>{@code request.searchType()} when non-null</li>
 *   <li>The first target namespace's
 *       {@link NamespaceConfig#architecture()} when available</li>
 *   <li>{@link SearchType#FULL_TEXT} as a fallback</li>
 * </ol>
 */
public final class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final NamespaceRepository namespaces;
    private final LuceneFullTextSearcher fullTextSearcher;
    private final LuceneVectorSearcher vectorSearcher;
    private final HybridSearchOrchestrator hybrid;

    public SearchService(final NamespaceRepository namespaces,
                         final LuceneFullTextSearcher fullTextSearcher,
                         final LuceneVectorSearcher vectorSearcher,
                         final HybridSearchOrchestrator hybrid) {
        this.namespaces = Objects.requireNonNull(namespaces);
        this.fullTextSearcher = Objects.requireNonNull(fullTextSearcher);
        this.vectorSearcher = Objects.requireNonNull(vectorSearcher);
        this.hybrid = Objects.requireNonNull(hybrid);
    }

    public SearchResult search(final SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        final long start = System.nanoTime();
        final List<String> targets = resolveTargets(request);
        if (targets.isEmpty()) {
            return SearchResult.empty(elapsedMs(start));
        }
        final SearchType type = resolveType(request, targets);
        log.debug("executing search type={} targets={}", type, targets);

        return switch (type) {
            case FULL_TEXT -> aggregate(targets, request,
                fullTextSearcher::search, start);
            case VECTOR -> aggregate(targets, request,
                vectorSearcher::search, start);
            case HYBRID -> aggregate(targets, request,
                this::hybridSearch, start);
        };
    }

    private SearchResult aggregate(final List<String> targets,
                                   final SearchRequest request,
                                   final BiFunction<String, SearchRequest, SearchResult> engine,
                                   final long startNanos) {
        if (targets.size() == 1) {
            return applyIndexWeight(targets.get(0), engine.apply(targets.get(0), request));
        }

        final SearchRequest expanded = expandFor(request);
        final List<SearchHit> all = new ArrayList<>();
        long totalHits = 0L;
        for (final String namespaceId : targets) {
            final SearchResult partial = applyIndexWeight(namespaceId, engine.apply(namespaceId, expanded));
            totalHits += partial.totalHits();
            all.addAll(partial.hits());
        }
        all.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        final int from = Math.min(request.pagination().offset(), all.size());
        final int to = Math.min(from + request.pagination().limit(), all.size());
        final List<SearchHit> page = all.subList(from, to);
        final double maxScore = all.isEmpty() ? 0.0 : all.get(0).score();
        return new SearchResult(page, totalHits, maxScore, Map.of(), elapsedMs(startNanos));
    }

    /**
     * Multiply every hit's score by the namespace's {@code indexWeight}.
     * When the weight is exactly {@code 1.0} (the default) the result is
     * returned unchanged to avoid allocating new {@link SearchHit} records.
     */
    private SearchResult applyIndexWeight(final String namespaceId, final SearchResult result) {
        final double weight = namespaces.findById(namespaceId)
            .map(Namespace::config)
            .map(NamespaceConfig::indexWeight)
            .orElse(NamespaceConfig.DEFAULT_INDEX_WEIGHT);
        if (weight == NamespaceConfig.DEFAULT_INDEX_WEIGHT || result.hits().isEmpty()) {
            return result;
        }
        final List<SearchHit> scaled = new ArrayList<>(result.hits().size());
        for (final SearchHit h : result.hits()) {
            scaled.add(new SearchHit(h.documentId(), h.namespaceId(), h.title(),
                h.content(), h.score() * weight, h.highlights(), h.metadata()));
        }
        final double maxScore = scaled.isEmpty() ? 0.0 : scaled.get(0).score();
        return new SearchResult(scaled, result.totalHits(), maxScore,
            result.aggregations(), result.tookMs());
    }

    private SearchResult hybridSearch(final String namespaceId, final SearchRequest request) {
        final NamespaceConfig config = namespaces.findById(namespaceId)
            .map(Namespace::config)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + namespaceId));
        if (config.searchStrategy() == SearchStrategy.PARALLEL) {
            return hybrid.parallel(namespaceId, request);
        }
        return hybrid.sequential(namespaceId, request, config.searchOrder());
    }

    private SearchType resolveType(final SearchRequest request, final List<String> targets) {
        if (request.searchType() != null) {
            return request.searchType();
        }
        return targets.stream()
            .findFirst()
            .flatMap(namespaces::findById)
            .map(Namespace::config)
            .map(NamespaceConfig::architecture)
            .orElse(SearchType.FULL_TEXT);
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

    private SearchRequest expandFor(final SearchRequest request) {
        final PaginationParams page = request.pagination();
        return SearchRequest.builder()
            .query(request.query())
            .namespaceIds(List.of())
            .searchType(request.searchType())
            .options(request.options())
            .pagination(new PaginationParams(0, page.offset() + page.limit()))
            .filters(request.filters())
            .build();
    }

    private long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
