package com.searchable.api.controller.request;

import com.searchable.api.controller.payload.DocumentInput;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Body for {@code POST /api/v1/index/batch}. */
public record BatchIndexRequest(@NotBlank String namespaceId, List<DocumentInput> documents) { }
