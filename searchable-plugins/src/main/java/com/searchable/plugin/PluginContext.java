package com.searchable.plugin;

import java.util.Map;
import java.util.Objects;

/**
 * Context passed to plugins on initialization, exposing user-supplied
 * configuration and a logger handle.
 *
 * @param namespaceId target namespace
 * @param config      plugin-specific configuration values
 */
public record PluginContext(String namespaceId, Map<String, Object> config) {

    public PluginContext {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
