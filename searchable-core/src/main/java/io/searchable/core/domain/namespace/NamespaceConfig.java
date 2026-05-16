package io.searchable.core.domain.namespace;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-namespace configuration controlling search behavior.
 *
 * <p>{@code indexWeight} is the multiplier applied to every hit returned
 * from this namespace when results are aggregated across multiple namespaces.
 * Use values {@code > 1.0} to boost the namespace, {@code 0 &lt; w &lt; 1.0}
 * to suppress it. The default of {@code 1.0} is neutral.
 */
public record NamespaceConfig(
    SearchType architecture,
    SearchStrategy searchStrategy,
    SearchOrder searchOrder,
    EmbeddingConfig embeddingConfig,
    AiConfig aiConfig,
    double indexWeight,
    Map<String, Object> customParams
) {

    public static final double DEFAULT_INDEX_WEIGHT = 1.0;

    public NamespaceConfig {
        Objects.requireNonNull(architecture, "architecture must not be null");
        Objects.requireNonNull(searchStrategy, "searchStrategy must not be null");
        Objects.requireNonNull(searchOrder, "searchOrder must not be null");
        Objects.requireNonNull(aiConfig, "aiConfig must not be null");
        if (indexWeight < 0.0 || Double.isNaN(indexWeight) || Double.isInfinite(indexWeight)) {
            throw new IllegalArgumentException(
                "indexWeight must be a finite non-negative number, was " + indexWeight);
        }
        customParams = customParams == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(customParams));
    }

    /**
     * Backward-compatible constructor that omits {@code indexWeight};
     * {@link #DEFAULT_INDEX_WEIGHT} is applied.
     */
    public NamespaceConfig(final SearchType architecture,
                           final SearchStrategy searchStrategy,
                           final SearchOrder searchOrder,
                           final EmbeddingConfig embeddingConfig,
                           final AiConfig aiConfig,
                           final Map<String, Object> customParams) {
        this(architecture, searchStrategy, searchOrder, embeddingConfig, aiConfig,
            DEFAULT_INDEX_WEIGHT, customParams);
    }

    /** Default configuration: full-text only, sequential strategy, no AI. */
    public static NamespaceConfig defaults() {
        return new NamespaceConfig(
            SearchType.FULL_TEXT,
            SearchStrategy.SEQUENTIAL,
            SearchOrder.FULL_TEXT_FIRST,
            null,
            AiConfig.disabled(),
            DEFAULT_INDEX_WEIGHT,
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
