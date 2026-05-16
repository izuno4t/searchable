package io.searchable.core.domain.dictionary;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for {@link UserDictionary} (file-backed, DB-backed, etc.).
 */
public interface UserDictionaryRepository {

    /** Insert or replace the dictionary at the given scope. */
    void save(UserDictionary dictionary);

    /** Load the dictionary at the given scope, if it exists. */
    Optional<UserDictionary> find(DictionaryScope scope);

    /** Return all stored dictionaries (global + every namespace) for browsing. */
    List<UserDictionary> findAll();

    /** Delete the dictionary at the given scope. */
    boolean delete(DictionaryScope scope);
}
