package com.searchable.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.namespace.NamespaceConfig;
import com.searchable.core.domain.namespace.NamespaceConfigPatch;
import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Container for namespace-related DTOs.
 */
public final class NamespaceDtos {

    private NamespaceDtos() { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfigDto(SearchType architecture,
                            SearchStrategy searchStrategy,
                            SearchOrder searchOrder,
                            Map<String, Object> customParams) {

        public static ConfigDto from(final NamespaceConfig config) {
            return new ConfigDto(config.architecture(), config.searchStrategy(),
                config.searchOrder(), config.customParams());
        }

        public NamespaceConfigPatch toPatch() {
            return new NamespaceConfigPatch(architecture, searchStrategy, searchOrder,
                null, null, customParams);
        }
    }

    public record CreateRequest(@NotBlank String id, @NotBlank String name, ConfigDto config) { }

    public record UpdateRequest(String name, ConfigDto config) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(String id, String name, ConfigDto config,
                           Instant createdAt, Instant updatedAt) {

        public static Response from(final Namespace ns) {
            return new Response(ns.id(), ns.name(),
                ConfigDto.from(ns.config()), ns.createdAt(), ns.updatedAt());
        }
    }

    public record ListResponse(List<Response> namespaces, int total) {
        public static ListResponse from(final List<Namespace> ns) {
            final List<Response> responses = ns.stream().map(Response::from).toList();
            return new ListResponse(responses, responses.size());
        }
    }
}
