package com.searchable.plugin;

import java.util.List;
import java.util.stream.Stream;

/**
 * Plugin that supplies documents from an external data source.
 *
 * <p>Implementations are loaded via {@link java.util.ServiceLoader}; declare
 * the class under
 * {@code META-INF/services/com.searchable.plugin.DataSourcePlugin}.
 */
public interface DataSourcePlugin extends SearchablePlugin {

    /**
     * Fetch all documents currently available from this source.
     *
     * <p>Implementations may return a snapshot list or stream documents
     * lazily; the platform will close the stream when done.
     *
     * @param context per-namespace configuration / context
     */
    Stream<PluginDocument> fetch(PluginContext context);

    /** Convenience helper returning all documents in a single list. */
    default List<PluginDocument> fetchAll(final PluginContext context) {
        try (Stream<PluginDocument> stream = fetch(context)) {
            return stream.toList();
        }
    }
}
