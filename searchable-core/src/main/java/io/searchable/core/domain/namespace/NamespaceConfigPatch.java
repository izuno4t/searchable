package io.searchable.core.domain.namespace;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;

import java.util.Map;

/**
 * Partial namespace configuration where every field is optional.
 *
 * <p>Used by application services to merge user-supplied overrides on top
 * of global defaults before constructing the strict {@link NamespaceConfig}.
 */
public record NamespaceConfigPatch(
    SearchType architecture,
    SearchStrategy searchStrategy,
    SearchOrder searchOrder,
    EmbeddingConfig embeddingConfig,
    AiConfig aiConfig,
    Map<String, Object> customParams
) {

    public static NamespaceConfigPatch empty() {
        return new NamespaceConfigPatch(null, null, null, null, null, null);
    }
}
