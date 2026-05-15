package com.searchable.core.application;

import com.searchable.core.application.config.GlobalConfig;
import com.searchable.core.application.config.GlobalConfigProvider;
import com.searchable.core.domain.index.IndexMetadata;
import com.searchable.core.domain.index.IndexMetadataRepository;
import com.searchable.core.domain.namespace.AiConfig;
import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.namespace.NamespaceConfig;
import com.searchable.core.domain.namespace.NamespaceConfigPatch;
import com.searchable.core.domain.namespace.NamespaceRepository;
import com.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Application-layer service for namespace lifecycle and configuration.
 *
 * <p>Coordinates the namespace repository, index metadata, and Lucene index
 * provider so callers can treat namespace operations as atomic units.
 */
public final class NamespaceService {

    private static final Logger log = LoggerFactory.getLogger(NamespaceService.class);

    private final NamespaceRepository namespaces;
    private final IndexMetadataRepository indexMetadata;
    private final LuceneIndexProvider indexProvider;
    private final GlobalConfigProvider globalConfigProvider;
    private final Clock clock;

    public NamespaceService(final NamespaceRepository namespaces,
                            final IndexMetadataRepository indexMetadata,
                            final LuceneIndexProvider indexProvider,
                            final GlobalConfig globalConfig,
                            final Clock clock) {
        this(namespaces, indexMetadata, indexProvider,
            new GlobalConfigProvider(globalConfig), clock);
    }

    public NamespaceService(final NamespaceRepository namespaces,
                            final IndexMetadataRepository indexMetadata,
                            final LuceneIndexProvider indexProvider,
                            final GlobalConfigProvider globalConfigProvider,
                            final Clock clock) {
        this.namespaces = Objects.requireNonNull(namespaces);
        this.indexMetadata = Objects.requireNonNull(indexMetadata);
        this.indexProvider = Objects.requireNonNull(indexProvider);
        this.globalConfigProvider = Objects.requireNonNull(globalConfigProvider);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Create a new namespace. The provided config is merged with the global
     * defaults for any field left {@code null}.
     *
     * @throws IllegalStateException if a namespace with the same id exists
     */
    public Namespace create(final String id, final String name, final NamespaceConfigPatch patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (namespaces.exists(id)) {
            throw new IllegalStateException("Namespace already exists: " + id);
        }
        final Instant now = clock.instant();
        final NamespaceConfig effective = applyDefaults(patch);
        final Namespace ns = new Namespace(id, name, effective, now, now);

        namespaces.save(ns);
        indexMetadata.save(IndexMetadata.empty(id, now));
        indexProvider.getOrCreate(id);

        log.info("created namespace {} ({})", id, name);
        return ns;
    }

    public Optional<Namespace> findById(final String id) {
        return namespaces.findById(id);
    }

    public List<Namespace> listAll() {
        return namespaces.findAll();
    }

    /** Replace the namespace configuration; omitted fields fall back to global defaults. */
    public Namespace updateConfig(final String id, final NamespaceConfigPatch patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");
        final Namespace existing = namespaces.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + id));

        final Namespace updated = existing.withConfig(applyDefaults(patch), clock.instant());
        namespaces.save(updated);
        log.info("updated config for namespace {}", id);
        return updated;
    }

    /** Rename the namespace (id stays immutable). */
    public Namespace rename(final String id, final String newName) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(newName, "newName must not be null");
        final Namespace existing = namespaces.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + id));

        final Namespace updated = existing.withName(newName, clock.instant());
        namespaces.save(updated);
        log.info("renamed namespace {} to '{}'", id, newName);
        return updated;
    }

    /**
     * Delete the namespace, its index metadata (cascade), and the Lucene
     * index directory. Returns whether anything existed.
     */
    public boolean delete(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        final boolean removedFromDb = namespaces.delete(id);
        try {
            indexProvider.remove(id, true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to clean up index for " + id, e);
        }
        if (removedFromDb) {
            log.info("deleted namespace {}", id);
        }
        return removedFromDb;
    }

    private NamespaceConfig applyDefaults(final NamespaceConfigPatch patch) {
        final NamespaceConfigPatch p = patch == null ? NamespaceConfigPatch.empty() : patch;
        final GlobalConfig globalConfig = globalConfigProvider.current();
        return new NamespaceConfig(
            p.architecture() != null ? p.architecture() : globalConfig.defaultArchitecture(),
            p.searchStrategy() != null ? p.searchStrategy()
                : globalConfig.defaultSearchStrategy(),
            p.searchOrder() != null ? p.searchOrder() : globalConfig.defaultSearchOrder(),
            p.embeddingConfig(),
            p.aiConfig() != null ? p.aiConfig() : AiConfig.disabled(),
            p.customParams()
        );
    }
}
