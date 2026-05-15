package com.searchable.core.application.config;

import java.nio.file.Path;

/**
 * Configuration of the plugin loader.
 *
 * @param directory directory of plugin JARs ({@code null} means classpath only)
 */
public record PluginsConfig(Path directory) {

    public static PluginsConfig classpathOnly() {
        return new PluginsConfig(null);
    }
}
