package com.searchable.api.controller.request;

import com.searchable.api.controller.payload.DictionaryEntryPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Body for {@code PUT /api/v1/dictionaries/...} endpoints.
 */
public record UserDictionaryUpsertRequest(
    @NotBlank String name,
    List<@Valid DictionaryEntryPayload> entries
) { }
