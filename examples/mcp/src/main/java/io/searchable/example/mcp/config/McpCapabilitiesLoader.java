package io.searchable.example.mcp.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reads {@link McpCapabilitiesConfig} from a YAML source.
 *
 * <p>Property names follow {@code kebab-case} (e.g. {@code server-info}),
 * matching the convention used by the core {@code searchable.yaml} loader.
 */
public final class McpCapabilitiesLoader {

    private final ObjectMapper objectMapper;

    public McpCapabilitiesLoader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public McpCapabilitiesConfig load(final Path file) {
        Objects.requireNonNull(file, "file must not be null");
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read MCP capabilities from " + file, e);
        }
    }

    public McpCapabilitiesConfig load(final InputStream input) {
        Objects.requireNonNull(input, "input must not be null");
        try {
            return objectMapper.readValue(input, McpCapabilitiesConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse MCP capabilities YAML", e);
        }
    }
}
