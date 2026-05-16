package io.searchable.core.application;

import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexMetadataRepository;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceRepository;

import java.time.Instant;
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
        final List<Namespace> all = namespaces.findAll();
        long totalDocs = 0L;
        long totalBytes = 0L;
        Instant lastUpdated = null;

        for (final Namespace ns : all) {
            final Optional<IndexMetadata> md = indexMetadata.findByNamespaceId(ns.id());
            if (md.isEmpty()) {
                continue;
            }
            totalDocs += md.get().documentCount();
            totalBytes += md.get().indexSizeBytes();
            final Instant ts = md.get().lastUpdated();
            if (lastUpdated == null || ts.isAfter(lastUpdated)) {
                lastUpdated = ts;
            }
        }

        return new Statistics(all.size(), totalDocs, totalBytes, lastUpdated);
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
}
