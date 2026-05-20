package io.searchable.core.application;

import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.domain.document.DocumentMetadataRepository;
import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.domain.search.SubResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResultEnricherTest {

    private static final Instant NOW = Instant.parse("2026-05-01T00:00:00Z");

    @Test
    void enrichAttachesParentMetadataToHits() {
        final StubRepository repo = new StubRepository();
        repo.put("ns", "doc-1", Map.of("url", "file:///abs/doc-1.md", "category", "guide"));
        final SearchResultEnricher enricher = new SearchResultEnricher(repo);

        final SearchHit raw = new SearchHit("doc-1", "ns", "Title", "body",
            1.0, Map.of(), Map.of(), List.of());
        final SearchResult result = new SearchResult(List.of(raw), 1L, 1.0, Map.of(), 0L);

        final SearchHit enriched = enricher.enrich(result).hits().get(0);
        assertThat(enriched.metadata())
            .containsEntry("url", "file:///abs/doc-1.md")
            .containsEntry("category", "guide");
    }

    @Test
    void enrichRebuildsSubResultAnchorUrlFromMetadataUrl() {
        final StubRepository repo = new StubRepository();
        repo.put("ns", "doc-1", Map.of("url", "https://example.com/page"));
        final SearchResultEnricher enricher = new SearchResultEnricher(repo);

        final SubResult sub = new SubResult(
            "doc-1#1", "doc-1", 2, "Installation", "...",
            0.5, Map.of(), null);
        final SearchHit raw = new SearchHit("doc-1", "ns", "Title", "body",
            1.0, Map.of(), Map.of(), List.of(sub));
        final SearchResult result = new SearchResult(List.of(raw), 1L, 1.0, Map.of(), 0L);

        final SubResult enrichedSub = enricher.enrich(result).hits().get(0).subResults().get(0);
        assertThat(enrichedSub.anchorUrl()).isEqualTo("https://example.com/page#installation");
    }

    @Test
    void enrichLeavesSubResultAnchorUrlNullWhenMetadataUrlMissing() {
        final StubRepository repo = new StubRepository();
        repo.put("ns", "doc-1", Map.of("category", "guide"));    // no url key
        final SearchResultEnricher enricher = new SearchResultEnricher(repo);

        final SubResult sub = new SubResult("doc-1#1", "doc-1", 1, "H1", "...",
            0.5, Map.of(), null);
        final SearchHit raw = new SearchHit("doc-1", "ns", "Title", "body",
            1.0, Map.of(), Map.of(), List.of(sub));
        final SearchResult result = new SearchResult(List.of(raw), 1L, 1.0, Map.of(), 0L);

        final SubResult enrichedSub = enricher.enrich(result).hits().get(0).subResults().get(0);
        assertThat(enrichedSub.anchorUrl()).isNull();
    }

    @Test
    void enrichGroupsLookupByNamespace() {
        final StubRepository repo = new StubRepository();
        repo.put("nsA", "a1", Map.of("url", "file:///a1"));
        repo.put("nsB", "b1", Map.of("url", "file:///b1"));
        final SearchResultEnricher enricher = new SearchResultEnricher(repo);

        final List<SearchHit> hits = List.of(
            new SearchHit("a1", "nsA", "A1", null, 1.0, Map.of(), Map.of(), List.of()),
            new SearchHit("b1", "nsB", "B1", null, 0.9, Map.of(), Map.of(), List.of()));
        final SearchResult enriched = enricher.enrich(
            new SearchResult(hits, 2L, 1.0, Map.of(), 0L));

        assertThat(enriched.hits()).hasSize(2);
        assertThat(enriched.hits().get(0).metadata()).containsEntry("url", "file:///a1");
        assertThat(enriched.hits().get(1).metadata()).containsEntry("url", "file:///b1");
        // Expect exactly one batch query per namespace (2 namespaces -> 2 calls).
        assertThat(repo.queryCount).isEqualTo(2);
    }

    @Test
    void enrichLeavesHitUnchangedWhenRepositoryHasNoRecord() {
        final StubRepository repo = new StubRepository();   // empty
        final SearchResultEnricher enricher = new SearchResultEnricher(repo);

        final SearchHit raw = new SearchHit("missing", "ns", "T", "c",
            1.0, Map.of(), Map.of("local", "kept"), List.of());
        final SearchHit enriched = enricher
            .enrich(new SearchResult(List.of(raw), 1L, 1.0, Map.of(), 0L))
            .hits().get(0);
        assertThat(enriched.metadata()).containsExactlyEntriesOf(Map.of("local", "kept"));
    }

    @Test
    void enrichIsPassThroughWhenRepositoryIsNull() {
        final SearchResultEnricher enricher = new SearchResultEnricher(null);
        final SearchHit raw = new SearchHit("doc-1", "ns", "T", "c",
            1.0, Map.of(), Map.of(), List.of());
        final SearchResult result = new SearchResult(List.of(raw), 1L, 1.0, Map.of(), 0L);
        assertThat(enricher.enrich(result)).isSameAs(result);
    }

    @Test
    void enrichIsPassThroughForEmptyResult() {
        final StubRepository repo = new StubRepository();
        final SearchResultEnricher enricher = new SearchResultEnricher(repo);
        final SearchResult empty = SearchResult.empty(0L);
        assertThat(enricher.enrich(empty)).isSameAs(empty);
    }

    /** In-memory stub that records how many batched lookups were issued. */
    private static final class StubRepository implements DocumentMetadataRepository {
        int queryCount;
        private final List<DocumentMetadataRecord> rows = new ArrayList<>();

        void put(final String ns, final String id, final Map<String, Object> meta) {
            rows.add(new DocumentMetadataRecord(ns, id, "T", meta, NOW));
        }

        @Override public void save(final DocumentMetadataRecord record) { rows.add(record); }
        @Override public Optional<DocumentMetadataRecord> findById(final String ns, final String id) {
            return rows.stream().filter(r -> r.namespaceId().equals(ns) && r.documentId().equals(id))
                .findFirst();
        }
        @Override public List<DocumentMetadataRecord> findByIds(final String ns,
                                                                final Collection<String> ids) {
            queryCount++;
            return rows.stream()
                .filter(r -> r.namespaceId().equals(ns) && ids.contains(r.documentId()))
                .toList();
        }
        @Override public List<DocumentMetadataRecord> list(final String ns, final int o, final int l) {
            return rows.stream().filter(r -> r.namespaceId().equals(ns)).toList();
        }
        @Override public long count(final String ns) {
            return rows.stream().filter(r -> r.namespaceId().equals(ns)).count();
        }
        @Override public boolean delete(final String ns, final String id) { return false; }
        @Override public void deleteByNamespace(final String ns) { /* no-op */ }
    }
}
