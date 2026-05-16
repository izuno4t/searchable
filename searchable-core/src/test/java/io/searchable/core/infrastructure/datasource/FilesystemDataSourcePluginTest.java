package io.searchable.core.infrastructure.datasource;

import io.searchable.plugin.PluginContext;
import io.searchable.plugin.PluginDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemDataSourcePluginTest {

    @TempDir Path tempDir;

    @Test
    void readsMatchingFilesUnderDirectory() throws Exception {
        Files.writeString(tempDir.resolve("note.md"), "# 見出し\n本文");
        Files.writeString(tempDir.resolve("ignored.csv"), "a,b,c");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/readme.txt"), "サブディレクトリ");

        final FilesystemDataSourcePlugin plugin = new FilesystemDataSourcePlugin();
        final PluginContext ctx = new PluginContext("ns", Map.of("directory", tempDir.toString()));
        final List<PluginDocument> docs = plugin.fetchAll(ctx);

        assertThat(docs).extracting(PluginDocument::id)
            .containsExactlyInAnyOrder("note.md", "sub/readme.txt");
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.sourceType()).isEqualTo("file");
            assertThat(d.contentHash()).hasSize(64);
            assertThat(d.sourceUpdated()).isNotNull();
        });
    }

    @Test
    void respectsConfiguredExtensions() throws Exception {
        Files.writeString(tempDir.resolve("a.md"), "md");
        Files.writeString(tempDir.resolve("b.txt"), "txt");
        final FilesystemDataSourcePlugin plugin = new FilesystemDataSourcePlugin();
        final PluginContext ctx = new PluginContext("ns",
            Map.of("directory", tempDir.toString(), "extensions", List.of(".txt")));
        assertThat(plugin.fetchAll(ctx)).extracting(PluginDocument::id).containsExactly("b.txt");
    }

    @Test
    void missingDirectoryConfigThrows() {
        final FilesystemDataSourcePlugin plugin = new FilesystemDataSourcePlugin();
        assertThatThrownBy(() -> plugin.fetchAll(new PluginContext("ns", Map.of())))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
