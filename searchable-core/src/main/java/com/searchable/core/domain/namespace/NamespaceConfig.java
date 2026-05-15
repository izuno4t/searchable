package com.searchable.core.domain.namespace;

import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-namespace configuration controlling search behavior.
 */
public record NamespaceConfig(
    SearchType architecture,
    SearchStrategy searchStrategy,
    SearchOrder searchOrder,
    EmbeddingConfig embeddingConfig,
    AiConfig aiConfig,
    Map<String, Object> customParams
) {

    public NamespaceConfig {
        Objects.requireNonNull(architecture, "architecture must not be null");
        Objects.requireNonNull(searchStrategy, "searchStrategy must not be null");
        Objects.requireNonNull(searchOrder, "searchOrder must not be null");
        Objects.requireNonNull(aiConfig, "aiConfig must not be null");
        customParams = customParams == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(customParams));
    }

    /** Default configuration: full-text only, sequential strategy, no AI. */
    public static NamespaceConfig defaults() {
        return new NamespaceConfig(
            SearchType.FULL_TEXT,
            SearchStrategy.SEQUENTIAL,
            SearchOrder.FULL_TEXT_FIRST,
            null,
            AiConfig.disabled(),
            Map.of()
        );
    }

    public boolean isFullTextEnabled() {
        return architecture == SearchType.FULL_TEXT || architecture == SearchType.HYBRID;
    }

    public boolean isVectorEnabled() {
        return architecture == SearchType.VECTOR || architecture == SearchType.HYBRID;
    }
}
