package io.searchable.example.mcp.config;

import java.util.Map;
import java.util.Objects;

/**
 * MCP server's {@code InitializeResult} payload, minus {@code protocolVersion}
 * which is bound to the implementation.
 *
 * <p>Maps directly to the YAML file loaded by {@link McpCapabilitiesLoader}.
 * The {@code serverInfo} block corresponds to the MCP spec's
 * {@code Implementation} object; {@code capabilities} to
 * {@code ServerCapabilities}.
 */
public record McpCapabilitiesConfig(
    ServerInfo serverInfo,
    Map<String, Object> capabilities
) {

    public McpCapabilitiesConfig {
        Objects.requireNonNull(serverInfo, "serverInfo must not be null");
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
    }

    public record ServerInfo(String name, String version) {

        public ServerInfo {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(version, "version must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (version.isBlank()) {
                throw new IllegalArgumentException("version must not be blank");
            }
        }
    }
}
