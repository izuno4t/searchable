package io.searchable.example.api.controller.request;

import io.searchable.example.api.controller.payload.NamespaceConfigPayload;

/** Body for {@code PUT /api/v1/namespaces/{id}}. */
public record NamespaceUpdateRequest(String name, NamespaceConfigPayload config) { }
