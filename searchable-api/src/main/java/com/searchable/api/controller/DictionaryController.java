package com.searchable.api.controller;

import com.searchable.api.controller.request.UserDictionaryUpsertRequest;
import com.searchable.api.controller.response.UserDictionaryListResponse;
import com.searchable.api.controller.response.UserDictionaryResponse;
import com.searchable.core.domain.dictionary.DictionaryScope;
import com.searchable.core.domain.dictionary.UserDictionary;
import com.searchable.core.domain.dictionary.UserDictionaryEntry;
import com.searchable.core.domain.dictionary.UserDictionaryRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Manages Kuromoji user dictionaries scoped globally or per-namespace.
 *
 * <p>Path layout uses friendly scope segments rather than encoded
 * {@link DictionaryScope#key()} strings:
 * <ul>
 *   <li>{@code /api/v1/dictionaries/global}</li>
 *   <li>{@code /api/v1/dictionaries/namespaces/{namespaceId}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryController {

    private final UserDictionaryRepository repository;
    private final Clock clock;

    public DictionaryController(final UserDictionaryRepository repository, final Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping
    public UserDictionaryListResponse list() {
        return UserDictionaryListResponse.from(repository.findAll());
    }

    @GetMapping("/global")
    public UserDictionaryResponse getGlobal() {
        return UserDictionaryResponse.from(repository.find(DictionaryScope.GLOBAL)
            .orElseThrow(() -> new NoSuchElementException("No global dictionary")));
    }

    @PutMapping("/global")
    public UserDictionaryResponse putGlobal(
            @Valid @RequestBody final UserDictionaryUpsertRequest body) {
        return upsert(DictionaryScope.GLOBAL, body);
    }

    @DeleteMapping("/global")
    public ResponseEntity<Void> deleteGlobal() {
        return repository.delete(DictionaryScope.GLOBAL)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    @GetMapping("/namespaces/{namespaceId}")
    public UserDictionaryResponse getNamespace(@PathVariable final String namespaceId) {
        return UserDictionaryResponse.from(repository.find(DictionaryScope.namespace(namespaceId))
            .orElseThrow(() -> new NoSuchElementException(
                "No dictionary for namespace " + namespaceId)));
    }

    @PutMapping("/namespaces/{namespaceId}")
    public UserDictionaryResponse putNamespace(
            @PathVariable final String namespaceId,
            @Valid @RequestBody final UserDictionaryUpsertRequest body) {
        return upsert(DictionaryScope.namespace(namespaceId), body);
    }

    @DeleteMapping("/namespaces/{namespaceId}")
    public ResponseEntity<Void> deleteNamespace(@PathVariable final String namespaceId) {
        return repository.delete(DictionaryScope.namespace(namespaceId))
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    private UserDictionaryResponse upsert(final DictionaryScope scope,
                                          final UserDictionaryUpsertRequest body) {
        final List<UserDictionaryEntry> entries = body.entries() == null ? List.of()
            : body.entries().stream().map(p -> p.toDomain()).toList();
        final UserDictionary dictionary = new UserDictionary(scope, body.name(),
            entries, clock.instant());
        repository.save(dictionary);
        return UserDictionaryResponse.from(dictionary);
    }
}
