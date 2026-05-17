package io.searchable.plugin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourcePluginTest {

    @Test
    void fetchAllConsumesStreamFromFetch() {
        final PluginDocument doc = new PluginDocument(
            "d1", "title", "content", "stub", "/loc",
            null, Instant.parse("2026-01-01T00:00:00Z"), Map.of());

        final boolean[] streamClosed = { false };
        final DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override public String name() { return "stub"; }
            @Override public Stream<PluginDocument> fetch(final PluginContext context) {
                return Stream.of(doc).onClose(() -> streamClosed[0] = true);
            }
        };

        final List<PluginDocument> all = plugin.fetchAll(new PluginContext("ns", Map.of()));

        assertThat(all).containsExactly(doc);
        assertThat(streamClosed[0])
            .as("fetchAll must close the stream when finished")
            .isTrue();
    }

    @Test
    void fetchAllReturnsEmptyListForEmptyStream() {
        final DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override public String name() { return "empty"; }
            @Override public Stream<PluginDocument> fetch(final PluginContext context) {
                return Stream.empty();
            }
        };
        assertThat(plugin.fetchAll(new PluginContext("ns", Map.of()))).isEmpty();
    }
}
