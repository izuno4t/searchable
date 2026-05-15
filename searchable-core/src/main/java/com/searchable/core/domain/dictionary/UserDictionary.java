package com.searchable.core.domain.dictionary;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A user dictionary owned by one {@link DictionaryScope}.
 *
 * @param scope     ownership scope (global or specific namespace)
 * @param name      human-readable name (for management UI)
 * @param entries   ordered list of entries
 * @param updatedAt latest modification timestamp
 */
public record UserDictionary(
    DictionaryScope scope,
    String name,
    List<UserDictionaryEntry> entries,
    Instant updatedAt
) {

    public UserDictionary {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        entries = List.copyOf(entries);
    }

    public static UserDictionary empty(final DictionaryScope scope, final Instant now) {
        return new UserDictionary(scope, scope.key(), List.of(), now);
    }
}
