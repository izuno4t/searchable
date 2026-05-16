package io.searchable.core.application.config;

import io.searchable.core.infrastructure.persistence.PersistenceConfig;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Root application configuration.
 *
 * <p>Loaded from a YAML file by {@link ConfigLoader}.
 */
public record ApplicationConfig(
    Path dataDirectory,
    PersistenceConfig persistence,
    IndexConfig index,
    PluginsConfig plugins,
    GlobalConfig global
) {

    public ApplicationConfig {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(persistence, "persistence must not be null");
        index = index == null ? IndexConfig.defaults() : index;
        plugins = plugins == null ? PluginsConfig.classpathOnly() : plugins;
        global = global == null ? GlobalConfig.defaults() : global;
    }
}
