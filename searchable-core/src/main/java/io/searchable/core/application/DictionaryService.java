package io.searchable.core.application;

import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryRepository;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceRepository;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application-layer entry point for user-dictionary changes.
 *
 * <p>Saving a dictionary persists the new entries <em>and</em> reopens
 * the affected Lucene index contexts so future queries and ingests
 * pick up the change. Existing indexed terms keep their original
 * tokenization — to reflect the new dictionary against historical
 * documents the caller must trigger a rebuild
 * ({@link IndexService#rebuild(String)} or replay the source documents).
 *
 * <p>Per the task note for TASK-035, the dictionary update never wipes
 * existing index data automatically.
 */
public final class DictionaryService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryService.class);

    private final UserDictionaryRepository repository;
    private final LuceneIndexProvider indexProvider;
    private final NamespaceRepository namespaces;

    public DictionaryService(final UserDictionaryRepository repository,
                             final LuceneIndexProvider indexProvider,
                             final NamespaceRepository namespaces) {
        this.repository = Objects.requireNonNull(repository);
        this.indexProvider = Objects.requireNonNull(indexProvider);
        this.namespaces = Objects.requireNonNull(namespaces);
    }

    /** Persist the dictionary and notify all impacted namespaces. */
    public void save(final UserDictionary dictionary) {
        Objects.requireNonNull(dictionary, "dictionary must not be null");
        repository.save(dictionary);
        refreshAffectedNamespaces(dictionary.scope());
        log.info("dictionary {} saved ({} entries); affected indexes reopened",
            dictionary.scope().key(), dictionary.entries().size());
    }

    /** Delete the dictionary and refresh the impacted namespaces. */
    public boolean delete(final DictionaryScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        final boolean removed = repository.delete(scope);
        if (removed) {
            refreshAffectedNamespaces(scope);
        }
        return removed;
    }

    public List<UserDictionary> listAll() {
        return repository.findAll();
    }

    public Optional<UserDictionary> find(final DictionaryScope scope) {
        return repository.find(scope);
    }

    private void refreshAffectedNamespaces(final DictionaryScope scope) {
        final List<String> targets = switch (scope) {
            case DictionaryScope.Global ignored ->
                namespaces.findAll().stream().map(Namespace::id).toList();
            case DictionaryScope.Namespace ns -> List.of(ns.namespaceId());
        };
        for (final String namespaceId : targets) {
            if (!indexProvider.isOpen(namespaceId)) {
                continue;
            }
            try {
                indexProvider.refreshAnalyzer(namespaceId);
            } catch (IOException e) {
                log.warn("failed to refresh analyzer for namespace {} after dictionary change",
                    namespaceId, e);
            }
        }
    }
}
