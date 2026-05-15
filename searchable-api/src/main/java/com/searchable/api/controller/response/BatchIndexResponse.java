package com.searchable.api.controller.response;

import com.searchable.api.controller.payload.BatchIndexResult;

import java.util.List;

/** Aggregate result returned by the batch index endpoint. */
public record BatchIndexResponse(
    int total,
    int succeeded,
    int failed,
    List<BatchIndexResult> results
) { }
