package com.searchable.core.domain.dictionary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the effective dictionary entries for a namespace by combining
 * the global dictionary with the namespace-specific dictionary.
 *
 * <p>The namespace dictionary effectively extends and overrides the global
 * one: entries with identical surface forms from the namespace dictionary
 * win over global entries.
 */
public final class UserDictionaryResolver {

    private final UserDictionaryRepository repository;

    public UserDictionaryResolver(final UserDictionaryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /**
     * Return merged entries for the given namespace. The namespace entry list
     * is appended after the global list and earlier entries with the same
     * surface form are dropped (last-wins).
     */
    public List<UserDictionaryEntry> resolveFor(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        final Map<String, UserDictionaryEntry> merged = new LinkedHashMap<>();
        addEntries(merged, repository.find(DictionaryScope.GLOBAL));
        addEntries(merged, repository.find(DictionaryScope.namespace(namespaceId)));
        return new ArrayList<>(merged.values());
    }

    private void addEntries(final Map<String, UserDictionaryEntry> target,
                            final Optional<UserDictionary> dictionary) {
        dictionary.ifPresent(d -> {
            for (final UserDictionaryEntry entry : d.entries()) {
                target.put(entry.surface(), entry);
            }
        });
    }
}
