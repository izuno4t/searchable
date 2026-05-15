package com.searchable.core.domain.dictionary;

import java.util.Objects;

/**
 * Scope of a {@link UserDictionary}.
 *
 * <p>{@link #GLOBAL} dictionaries apply to every namespace. Namespace-scoped
 * dictionaries layer additional entries on top of the global dictionary.
 */
public sealed interface DictionaryScope {

    /** Stable serialized representation (e.g. {@code GLOBAL} or {@code NAMESPACE:project-a}). */
    String key();

    /** Whether this scope applies to the given namespace. */
    boolean appliesTo(String namespaceId);

    Global GLOBAL = new Global();

    static DictionaryScope namespace(final String namespaceId) {
        return new Namespace(namespaceId);
    }

    static DictionaryScope fromKey(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        if ("GLOBAL".equals(key)) {
            return GLOBAL;
        }
        if (key.startsWith("NAMESPACE:")) {
            return namespace(key.substring("NAMESPACE:".length()));
        }
        throw new IllegalArgumentException("Unknown scope key: " + key);
    }

    /** Global scope; applies to every namespace. */
    record Global() implements DictionaryScope {
        @Override public String key() { return "GLOBAL"; }
        @Override public boolean appliesTo(final String namespaceId) { return true; }
    }

    /** Namespace-specific scope. */
    record Namespace(String namespaceId) implements DictionaryScope {
        public Namespace {
            Objects.requireNonNull(namespaceId, "namespaceId must not be null");
            if (namespaceId.isBlank()) {
                throw new IllegalArgumentException("namespaceId must not be blank");
            }
        }
        @Override public String key() { return "NAMESPACE:" + namespaceId; }
        @Override public boolean appliesTo(final String namespaceId) {
            return this.namespaceId.equals(namespaceId);
        }
    }
}
