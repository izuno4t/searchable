package io.searchable.example.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpCapabilitiesLoaderTest {

    @Test
    void loadsBundledSampleFile() {
        final Path sample = Path.of("mcp-capabilities.yaml");
        final McpCapabilitiesConfig config = new McpCapabilitiesLoader().load(sample);

        assertThat(config.serverInfo().name()).isEqualTo("searchable-mcp");
        assertThat(config.serverInfo().version()).isNotBlank();
        assertThat(config.capabilities()).containsKey("tools");
        assertThat(config.instructions()).isNotBlank();
        assertThat(config.tools()).containsKey("search_documents");
        assertThat(config.tools().get("search_documents").description()).isNotBlank();
    }

    @Test
    void omittedVersionFallsBackToBuildVersion(@TempDir final Path tmp) throws Exception {
        final Path yaml = tmp.resolve("no-version.yaml");
        Files.writeString(yaml, """
            server-info:
              name: searchable-mcp
            capabilities:
              tools: {}
            """);

        final McpCapabilitiesConfig config = new McpCapabilitiesLoader().load(yaml);

        // Version must be populated; either the real Maven version (when
        // resources have been filtered) or the VERSION_FALLBACK sentinel.
        assertThat(config.serverInfo().version()).isNotBlank();
        assertThat(config.serverInfo().version())
            .isEqualTo(McpCapabilitiesLoader.defaultVersion());
    }

    @Test
    void blankVersionFallsBackToBuildVersion(@TempDir final Path tmp) throws Exception {
        final Path yaml = tmp.resolve("blank-version.yaml");
        Files.writeString(yaml, """
            server-info:
              name: searchable-mcp
              version: ""
            capabilities:
              tools: {}
            """);

        final McpCapabilitiesConfig config = new McpCapabilitiesLoader().load(yaml);

        assertThat(config.serverInfo().version())
            .isEqualTo(McpCapabilitiesLoader.defaultVersion());
    }

    @Test
    void loadsFromFile(@TempDir final Path tmp) throws Exception {
        final Path yaml = tmp.resolve("mcp.yaml");
        Files.writeString(yaml, """
            server-info:
              name: custom-mcp
              version: 9.9.9
            capabilities:
              tools: {}
              resources:
                listChanged: true
            """);

        final McpCapabilitiesConfig config = new McpCapabilitiesLoader().load(yaml);

        assertThat(config.serverInfo().name()).isEqualTo("custom-mcp");
        assertThat(config.serverInfo().version()).isEqualTo("9.9.9");
        assertThat(config.capabilities()).containsKeys("tools", "resources");
    }

    @Test
    void missingServerInfoIsRejected(@TempDir final Path tmp) throws Exception {
        final Path yaml = tmp.resolve("broken.yaml");
        Files.writeString(yaml, """
            capabilities:
              tools: {}
            """);

        assertThatThrownBy(() -> new McpCapabilitiesLoader().load(yaml))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankNameIsRejected(@TempDir final Path tmp) throws Exception {
        final Path yaml = tmp.resolve("blank.yaml");
        Files.writeString(yaml, """
            server-info:
              name: ""
              version: 1.0.0
            capabilities:
              tools: {}
            """);

        assertThatThrownBy(() -> new McpCapabilitiesLoader().load(yaml))
            .isInstanceOf(IllegalStateException.class);
    }
}
