package com.searchable.plugin;

/**
 * Base type for all Searchable plugins.
 *
 * <p>Implementations are loaded via Java's {@link java.util.ServiceLoader},
 * so each plugin JAR must declare its concrete class under
 * {@code META-INF/services/<plugin-interface>}.
 */
public interface SearchablePlugin {

    /** Stable unique name of the plugin (lowercase, e.g. {@code filesystem}). */
    String name();
}
