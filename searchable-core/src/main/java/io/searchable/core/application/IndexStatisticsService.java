package io.searchable.core.application;

import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexMetadataRepository;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregates index metadata across all namespaces for the dashboard.
 */
public final class IndexStatisticsService {

    private final NamespaceRepository namespaces;
    private final IndexMetadataRepository indexMetadata;

    public IndexStatisticsService(final NamespaceRepository namespaces,
                                  final IndexMetadataRepository indexMetadata) {
        this.namespaces = Objects.requireNonNull(namespaces);
        this.indexMetadata = Objects.requireNonNull(indexMetadata);
    }

    public Statistics aggregate() {
        return snapshot().aggregate();
    }

    /**
     * Gather aggregate + per-namespace statistics in a single pass.
     *
     * <p>Apps (webapp/mcp/api) use this when rendering a status banner
     * at startup or after a SIGHUP-triggered refresh. Rendering belongs
     * in the app layer; this method only returns data.
     */
    public StatusSnapshot snapshot() {
        final List<Namespace> all = namespaces.findAll();
        long totalDocs = 0L;
        long totalBytes = 0L;
        Instant lastUpdated = null;
        final List<NamespaceEntry> entries = new ArrayList<>(all.size());

        for (final Namespace ns : all) {
            final Optional<IndexMetadata> md = indexMetadata.findByNamespaceId(ns.id());
            final long docs = md.map(IndexMetadata::documentCount).orElse(0L);
            final long bytes = md.map(IndexMetadata::indexSizeBytes).orElse(0L);
            final Instant ts = md.map(IndexMetadata::lastUpdated).orElse(null);
            entries.add(new NamespaceEntry(ns.id(), docs, bytes, ts));
            if (md.isPresent()) {
                totalDocs += docs;
                totalBytes += bytes;
                if (lastUpdated == null || ts.isAfter(lastUpdated)) {
                    lastUpdated = ts;
                }
            }
        }

        final Statistics aggregate = new Statistics(all.size(), totalDocs, totalBytes, lastUpdated);
        return new StatusSnapshot(aggregate, List.copyOf(entries));
    }

    /**
     * Dashboard-friendly statistics summary.
     *
     * @param namespaceCount total number of namespaces
     * @param documentCount  sum of documents across all namespaces
     * @param indexSizeBytes sum of index sizes (bytes)
     * @param lastUpdated    most recent {@code lastUpdated} timestamp (nullable when empty)
     */
    public record Statistics(int namespaceCount, long documentCount,
                             long indexSizeBytes, Instant lastUpdated) { }

    /**
     * Per-namespace counts used by status banners. {@code lastUpdated}
     * is {@code null} when no {@code IndexMetadata} row exists for the
     * namespace yet (e.g. created but never ingested).
     */
    public record NamespaceEntry(String namespaceId, long documentCount,
                                 long indexSizeBytes, Instant lastUpdated) { }

    /**
     * Bundled snapshot returned by {@link #snapshot()}: aggregate
     * totals plus the per-namespace breakdown, both captured in one pass.
     */
    public record StatusSnapshot(Statistics aggregate, List<NamespaceEntry> perNamespace) { }
}
