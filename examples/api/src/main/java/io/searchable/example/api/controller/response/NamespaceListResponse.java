package io.searchable.example.api.controller.response;

import io.searchable.core.domain.namespace.Namespace;

import java.util.List;

/** Response for {@code GET /api/v1/namespaces}. */
public record NamespaceListResponse(List<NamespaceResponse> namespaces, int total) {

    public static NamespaceListResponse from(final List<Namespace> ns) {
        final List<NamespaceResponse> responses = ns.stream().map(NamespaceResponse::from).toList();
        return new NamespaceListResponse(responses, responses.size());
    }
}
