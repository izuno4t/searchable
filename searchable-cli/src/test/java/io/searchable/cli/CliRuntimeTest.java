package io.searchable.cli;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.config.SearchableConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliRuntimeTest {

    @TempDir Path tempDir;

    @Test
    void loadConfigRejectsNullPath() {
        assertThatThrownBy(() -> CliRuntime.loadConfig(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("configPath");
    }

    @Test
    void loadConfigParsesYamlIntoSearchableConfig() throws Exception {
        final Path config = writeConfig(tempDir);
        final SearchableConfig cfg = CliRuntime.loadConfig(config);

        assertThat(cfg.dataDirectory()).isEqualTo(tempDir);
        assertThat(cfg.persistence().type()).isEqualTo("H2");
        assertThat(cfg.index().directory()).isEqualTo(tempDir.resolve("indexes"));
    }

    @Test
    void openLibraryReturnsWritableLibrary() throws Exception {
        final Path config = writeConfig(tempDir);
        try (SearchableLibrary library = CliRuntime.openLibrary(config)) {
            assertThat(library.isReadOnly()).isFalse();
            assertThat(library.indexService()).isNotNull();
            assertThat(library.namespaceService()).isNotNull();
        }
    }

    @Test
    void openReadOnlyLibraryRejectsWriteServices() throws Exception {
        final Path config = writeConfig(tempDir);
        try (SearchableLibrary library = CliRuntime.openReadOnlyLibrary(config)) {
            assertThat(library.isReadOnly()).isTrue();
            assertThat(library.searchService()).isNotNull();
            assertThatThrownBy(library::indexService)
                .isInstanceOf(IllegalStateException.class);
        }
    }

    static Path writeConfig(final Path tempDir) throws java.io.IOException {
        return writeConfig(tempDir, "cli-runtime-test");
    }

    static Path writeConfig(final Path tempDir, final String dbName) throws java.io.IOException {
        final Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
            data-directory: %s
            persistence:
              type: H2
              url: "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
              username: sa
              password: ""
            index:
              directory: %s
            """.formatted(tempDir, dbName, tempDir.resolve("indexes")));
        return config;
    }
}
