package io.searchable.core.infrastructure.plugin;

import io.searchable.plugin.DataSourcePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginLoaderExtraTest {

    @TempDir Path tempDir;

    @Test
    void overviewListsDataSourcePlugins() {
        try (PluginLoader loader = new PluginLoader()) {
            final Map<String, java.util.List<String>> overview = loader.overview();
            assertThat(overview).containsKey("DataSourcePlugin");
            assertThat(overview.get("DataSourcePlugin")).contains("filesystem");
        }
    }

    @Test
    void apiRejectsNullArgs() {
        try (PluginLoader loader = new PluginLoader()) {
            assertThatThrownBy(() -> loader.loadAll(null))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> loader.findByName(DataSourcePlugin.class, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void closeOnLoaderWithNoExternalIsNoOp() {
        final PluginLoader loader = new PluginLoader();
        loader.close();
        loader.close(); // idempotent
    }

    @Test
    void secondLoadReusesCachedExternalClassLoader() throws IOException {
        // Build an empty jar so the external loader initialises on first call,
        // then call loadAll twice to exercise the cached (`externalClassLoader != null`)
        // branch of PluginLoader.externalClassLoader().
        final Path jar = tempDir.resolve("cached.jar");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("placeholder.txt"));
            zos.write("noop".getBytes());
            zos.closeEntry();
        }
        try (PluginLoader loader = new PluginLoader(tempDir)) {
            // First call → builds & caches the URLClassLoader.
            loader.loadDataSourcePlugins();
            // Second call → returns the cached classloader (covered branch).
            assertThat(loader.loadDataSourcePlugins())
                .extracting(DataSourcePlugin::name).contains("filesystem");
        }
    }

    @Test
    void closeAfterLoadingExternalJarReleasesClassloader() throws IOException {
        // Create a phantom (empty) jar so the external class loader gets built;
        // the loader does not try to read service files, so an empty jar is fine.
        final Path jar = tempDir.resolve("dummy.jar");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("placeholder.txt"));
            zos.write("noop".getBytes());
            zos.closeEntry();
        }

        try (PluginLoader loader = new PluginLoader(tempDir)) {
            // loadAll triggers externalClassLoader() initialization
            assertThat(loader.loadDataSourcePlugins())
                .extracting(DataSourcePlugin::name).contains("filesystem");
        }
    }
}
