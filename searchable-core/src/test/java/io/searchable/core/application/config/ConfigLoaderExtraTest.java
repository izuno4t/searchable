package io.searchable.core.application.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderExtraTest {

    @TempDir Path tempDir;

    @Test
    void rejectsNullFileOrInput() {
        final ConfigLoader loader = new ConfigLoader();
        assertThatThrownBy(() -> loader.load((Path) null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> loader.load((InputStream) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void wrapsIoExceptionWhenFileMissing() {
        assertThatThrownBy(() ->
            new ConfigLoader().load(tempDir.resolve("missing.yaml")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to read config");
    }

    @Test
    void loadsFromPath() throws IOException {
        final Path file = tempDir.resolve("config.yaml");
        Files.writeString(file, """
            data-directory: ./data
            persistence:
              type: H2
              url: "jdbc:h2:./x"
              username: sa
              password: ""
            """);
        final ApplicationConfig cfg = new ConfigLoader().load(file);
        // Per ADR-0002 the loader rewrites relative paths against the config
        // file's parent: data-directory and the embedded H2 file path both
        // resolve to <tempDir>/data and <tempDir>/data/x respectively.
        final Path expectedData = tempDir.toAbsolutePath().normalize().resolve("data");
        assertThat(cfg.dataDirectory()).isEqualTo(expectedData);
        // normalize() now also appends AUTO_SERVER=TRUE so the CLI can write
        // while an app holds the same H2 file open for reads.
        assertThat(cfg.persistence().url())
            .isEqualTo("jdbc:h2:" + expectedData.resolve("x") + ";AUTO_SERVER=TRUE");
    }

    @Test
    void invalidYamlInputWrappedAsIllegalState() {
        final String bad = "data-directory:\n  - this\n  - is\n  - a list\n";
        assertThatThrownBy(() -> new ConfigLoader()
            .load(new java.io.ByteArrayInputStream(bad.getBytes())))
            .isInstanceOf(IllegalStateException.class);
    }
}
