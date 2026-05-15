package com.searchable.api.controller.request;

import com.searchable.api.controller.payload.NamespaceConfigPayload;
import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/v1/namespaces}. */
public record NamespaceCreateRequest(
    @NotBlank String id,
    @NotBlank String name,
    NamespaceConfigPayload config
) { }
