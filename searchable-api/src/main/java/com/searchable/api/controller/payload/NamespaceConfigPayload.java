package com.searchable.api.controller.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.searchable.core.domain.namespace.NamespaceConfig;
import com.searchable.core.domain.namespace.NamespaceConfigPatch;
import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;

import java.util.Map;

/**
 * Wire representation of namespace configuration, used both in
 * request bodies (as user-supplied overrides) and in responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NamespaceConfigPayload(
    SearchType architecture,
    SearchStrategy searchStrategy,
    SearchOrder searchOrder,
    Map<String, Object> customParams
) {

    public static NamespaceConfigPayload from(final NamespaceConfig config) {
        return new NamespaceConfigPayload(config.architecture(), config.searchStrategy(),
            config.searchOrder(), config.customParams());
    }

    public NamespaceConfigPatch toPatch() {
        return new NamespaceConfigPatch(architecture, searchStrategy, searchOrder,
            null, null, customParams);
    }
}
