package com.searchable.api.controller.response;

import com.searchable.api.controller.payload.DictionaryEntryPayload;
import com.searchable.core.domain.dictionary.UserDictionary;

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
