package io.searchable.core.infrastructure.datasource;

import io.searchable.plugin.PluginContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemDataSourcePluginBranchTest {

    @TempDir Path tempDir;

    @Test
    void directoryThatIsNotADirectoryRejected() throws Exception {
        final Path file = tempDir.resolve("not-a-dir.txt");
        Files.writeString(file, "hi");
        final FilesystemDataSourcePlugin plugin = new FilesystemDataSourcePlugin();
        assertThatThrownBy(() -> plugin.fetchAll(new PluginContext("ns",
                Map.of("directory", file.toString()))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not exist or is not a directory");
    }

    @Test
    void nonStringElementInExtensionsFallsBackToDefault() throws Exception {
        Files.writeString(tempDir.resolve("a.md"), "md");
        final FilesystemDataSourcePlugin plugin = new FilesystemDataSourcePlugin();
        // extensions list contains a non-String entry -> default extensions applied
        final var docs = plugin.fetchAll(new PluginContext("ns", Map.of(
            "directory", tempDir.toString(),
            "extensions", List.of(1, 2))));
        assertThat(docs).hasSize(1); // .md is in the defaults
    }

    @Test
    void nonListExtensionsFallsBackToDefault() throws Exception {
        Files.writeString(tempDir.resolve("a.md"), "md");
        final FilesystemDataSourcePlugin plugin = new FilesystemDataSourcePlugin();
        final var docs = plugin.fetchAll(new PluginContext("ns", Map.of(
            "directory", tempDir.toString(),
            "extensions", "not-a-list")));
        assertThat(docs).hasSize(1);
    }

    @Test
    void caseInsensitiveExtensionMatching() throws Exception {
        Files.writeString(tempDir.resolve("README.MD"), "uppercase ext");
        final FilesystemDataSourcePlugin plugin = new FilesystemDataSourcePlugin();
        final var docs = plugin.fetchAll(new PluginContext("ns",
            Map.of("directory", tempDir.toString())));
        assertThat(docs).hasSize(1);
    }

    @Test
    void pluginNameIsFilesystem() {
        final FilesystemDataSourcePlugin p = new FilesystemDataSourcePlugin();
        assertThat(p.name()).isEqualTo("filesystem");
    }
}
