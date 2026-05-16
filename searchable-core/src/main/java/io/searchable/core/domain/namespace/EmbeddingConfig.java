package io.searchable.core.domain.namespace;

import java.util.Objects;

/**
 * Embedding model configuration for vector search (Phase 2).
 *
 * @param model     embedding model identifier (e.g. {@code multilingual-e5-small})
 * @param dimension vector dimension
 */
public record EmbeddingConfig(String model, int dimension) {

    public EmbeddingConfig {
        Objects.requireNonNull(model, "model must not be null");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive, was " + dimension);
        }
    }
}
