package io.searchable.core.domain.namespace;

import java.util.Objects;

/**
 * Optional AI integration configuration.
 *
 * @param enabled  whether AI augmentation is enabled
 * @param provider provider identifier (e.g. {@code openai}, {@code anthropic}, {@code ollama})
 * @param model    model identifier
 */
public record AiConfig(boolean enabled, String provider, String model) {

    public static AiConfig disabled() {
        return new AiConfig(false, null, null);
    }

    public AiConfig {
        if (enabled) {
            Objects.requireNonNull(provider, "provider must not be null when enabled");
            Objects.requireNonNull(model, "model must not be null when enabled");
        }
    }
}
