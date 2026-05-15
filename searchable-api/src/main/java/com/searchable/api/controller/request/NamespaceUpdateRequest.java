package com.searchable.api.controller.request;

import com.searchable.api.controller.payload.NamespaceConfigPayload;

/** Body for {@code PUT /api/v1/namespaces/{id}}. */
public record NamespaceUpdateRequest(String name, NamespaceConfigPayload config) { }
