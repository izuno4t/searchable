package io.searchable.example.api.controller.request;

import io.searchable.example.api.controller.payload.DictionaryEntryPayload;
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
