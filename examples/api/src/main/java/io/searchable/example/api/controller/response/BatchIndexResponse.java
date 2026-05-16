package io.searchable.example.api.controller.response;

import io.searchable.example.api.controller.payload.BatchIndexResult;

import java.util.List;

/** Aggregate result returned by the batch index endpoint. */
public record BatchIndexResponse(
    int total,
    int succeeded,
    int failed,
    List<BatchIndexResult> results
) { }
