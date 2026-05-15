package com.searchable.api.controller.request;

import com.searchable.api.controller.payload.DocumentInput;
import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/v1/index/documents}. */
public record IndexDocumentRequest(@NotBlank String namespaceId, DocumentInput document) { }
