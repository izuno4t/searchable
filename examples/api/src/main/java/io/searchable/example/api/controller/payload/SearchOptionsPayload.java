package io.searchable.example.api.controller.payload;

/** Per-request search options nested inside a search request body. */
public record SearchOptionsPayload(
    Boolean highlightEnabled,
    Integer maxResults,
    Integer offset
) { }
