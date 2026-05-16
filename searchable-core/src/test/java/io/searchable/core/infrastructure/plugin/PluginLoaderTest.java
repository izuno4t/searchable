package io.searchable.core.infrastructure.plugin;

import io.searchable.plugin.DataSourcePlugin;
import io.searchable.plugin.PluginContext;
import io.searchable.plugin.PluginDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PluginLoaderTest {

    @TempDir Path tempDir;

    @Test
    void returnsBuiltInFilesystemPluginWhenNoExternalPluginsRegistered() {
        try (PluginLoader loader = new PluginLoader()) {
            assertThat(loader.loadDataSourcePlugins())
                .extracting(DataSourcePlugin::name)
                .containsExactly("filesystem");
        }
    }

    @Test
    void emptyPluginDirectoryStillExposesBuiltInPlugins() {
        try (PluginLoader loader = new PluginLoader(tempDir)) {
            assertThat(loader.loadDataSourcePlugins())
                .extracting(DataSourcePlugin::name)
                .containsExactly("filesystem");
        }
    }

    @Test
    void nonExistentDirectoryStillExposesBuiltInPlugins() {
        try (PluginLoader loader = new PluginLoader(tempDir.resolve("missing"))) {
            assertThat(loader.loadDataSourcePlugins())
                .extracting(DataSourcePlugin::name)
                .containsExactly("filesystem");
        }
    }

    @Test
    void findByNameReturnsEmptyWhenAbsent() {
        try (PluginLoader loader = new PluginLoader()) {
            final Optional<DataSourcePlugin> found =
                loader.findByName(DataSourcePlugin.class, "missing");
            assertThat(found).isEmpty();
        }
    }

    @Test
    void findByNameReturnsBuiltInFilesystem() {
        try (PluginLoader loader = new PluginLoader()) {
            final Optional<DataSourcePlugin> found =
                loader.findByName(DataSourcePlugin.class, "filesystem");
            assertThat(found).isPresent();
        }
    }

    /** Marker test ensuring the plugin SPI compiles against the loader. */
    @Test
    void inlinePluginCanProduceDocument() {
        final DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override public String name() { return "inline"; }
            @Override public Stream<PluginDocument> fetch(final PluginContext context) {
                return Stream.of(new PluginDocument("id", "title", "content",
                    "memory", "test", null, null, null));
            }
        };

        final List<PluginDocument> docs = plugin.fetchAll(new PluginContext("ns", null));
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).id()).isEqualTo("id");
    }
}
