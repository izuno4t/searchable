package com.searchable.api.controller.response;

import com.searchable.core.domain.dictionary.UserDictionary;

import java.util.List;

/** Response for {@code GET /api/v1/dictionaries}. */
public record UserDictionaryListResponse(List<UserDictionaryResponse> dictionaries, int total) {

    public static UserDictionaryListResponse from(final List<UserDictionary> dicts) {
        final List<UserDictionaryResponse> mapped =
            dicts.stream().map(UserDictionaryResponse::from).toList();
        return new UserDictionaryListResponse(mapped, mapped.size());
    }
}
