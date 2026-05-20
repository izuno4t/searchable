package io.searchable.core.application;

import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.domain.document.DocumentMetadataRepository;
import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.domain.search.SubResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Attaches document-level metadata to a raw {@link SearchResult}.
 *
 * <p>The Lucene searchers return hits whose
 * {@code SearchHit.metadata} is empty and whose {@code SubResult.anchorUrl}
 * is {@code null} — parent metadata lives in
 * {@link DocumentMetadataRepository} and not on the chunks. This enricher
 * batch-loads the metadata for every hit in a single SQL round-trip
 * (one batched {@code IN} query per namespace touched) and rebuilds
 * each hit with the populated metadata. {@link SubResult#anchorUrl()}
 * is regenerated from {@code metadata.url + "#" + slug(heading)} via
 * {@link AnchorUrls}.
 *
 * <p>When the configured repository is {@code null}, {@link #enrich}
 * returns the input result unchanged — used by tests that build
 * {@link SearchService} without a metadata DB.
 */
public final class SearchResultEnricher {

    private final DocumentMetadataRepository repository;

    public SearchResultEnricher(final DocumentMetadataRepository repository) {
        this.repository = repository;
    }

    public SearchResult enrich(final SearchResult raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        if (repository == null || raw.hits().isEmpty()) {
            return raw;
        }

        // Group document ids per namespace so each batch fetch hits a
        // namespace-scoped index (the metadata DB query is
        // `WHERE namespace_id = ? AND document_id IN (...)`).
        final Map<String, Set<String>> idsByNamespace = new LinkedHashMap<>();
        for (final SearchHit hit : raw.hits()) {
            idsByNamespace
                .computeIfAbsent(hit.namespaceId(), k -> new LinkedHashSet<>())
                .add(hit.documentId());
        }

        final Map<DocKey, DocumentMetadataRecord> byKey = new HashMap<>();
        for (final Map.Entry<String, Set<String>> e : idsByNamespace.entrySet()) {
            for (final DocumentMetadataRecord rec : repository.findByIds(e.getKey(), e.getValue())) {
                byKey.put(new DocKey(rec.namespaceId(), rec.documentId()), rec);
            }
        }

        final List<SearchHit> enriched = new ArrayList<>(raw.hits().size());
        for (final SearchHit hit : raw.hits()) {
            final DocumentMetadataRecord rec = byKey.get(new DocKey(hit.namespaceId(), hit.documentId()));
            enriched.add(rebuildHit(hit, rec));
        }

        return new SearchResult(enriched, raw.totalHits(), raw.maxScore(),
            raw.aggregations(), raw.tookMs());
    }

    private SearchHit rebuildHit(final SearchHit hit, final DocumentMetadataRecord rec) {
        final Map<String, Object> metadata = rec == null ? hit.metadata() : rec.metadata();
        final String baseUrl = metadata.get("url") instanceof String u ? u : null;
        final List<SubResult> rebuiltSubs;
        if (hit.subResults().isEmpty() || baseUrl == null) {
            // Nothing to enrich (anchorUrl falls back to null when the
            // parent has no metadata.url).
            rebuiltSubs = hit.subResults();
        } else {
            rebuiltSubs = new ArrayList<>(hit.subResults().size());
            for (final SubResult sub : hit.subResults()) {
                rebuiltSubs.add(new SubResult(
                    sub.sectionId(), sub.parentDocumentId(), sub.level(),
                    sub.heading(), sub.content(), sub.score(), sub.highlights(),
                    AnchorUrls.anchorFor(baseUrl, sub.heading())));
            }
        }
        return new SearchHit(
            hit.documentId(), hit.namespaceId(), hit.title(), hit.content(),
            hit.score(), hit.highlights(), metadata, rebuiltSubs);
    }

    private record DocKey(String namespaceId, String documentId) { }
}
