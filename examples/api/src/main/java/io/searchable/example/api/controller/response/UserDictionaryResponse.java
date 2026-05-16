package io.searchable.example.api.controller.response;

import io.searchable.example.api.controller.payload.DictionaryEntryPayload;
import io.searchable.core.domain.dictionary.UserDictionary;

import java.time.Instant;
import java.util.List;

/** Response for {@code GET /api/v1/dictionaries/...} endpoints. */
public record UserDictionaryResponse(
    String scope,
    String name,
    List<DictionaryEntryPayload> entries,
    Instant updatedAt
) {

    public static UserDictionaryResponse from(final UserDictionary dictionary) {
        return new UserDictionaryResponse(
            dictionary.scope().key(),
            dictionary.name(),
            dictionary.entries().stream().map(DictionaryEntryPayload::from).toList(),
            dictionary.updatedAt()
        );
    }
}
