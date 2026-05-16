package io.searchable.example.api.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.searchable.example.api.controller.payload.NamespaceConfigPayload;
import io.searchable.core.domain.namespace.Namespace;

import java.time.Instant;

/** Response for namespace get/create/update endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NamespaceResponse(
    String id,
    String name,
    NamespaceConfigPayload config,
    Instant createdAt,
    Instant updatedAt
) {

    public static NamespaceResponse from(final Namespace ns) {
        return new NamespaceResponse(ns.id(), ns.name(),
            NamespaceConfigPayload.from(ns.config()), ns.createdAt(), ns.updatedAt());
    }
}
