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
 * {@code ServerCapabilities}; {@code instructions} is the spec's optional
 * server-level usage hint surfaced to LLMs in the {@code initialize}
 * response.
 *
 * <p>{@code tools} holds per-tool overrides keyed by the tool's registered
 * name. Each Java {@link io.searchable.example.mcp.tool.McpTool} provides
 * its default {@code description}; a matching entry here replaces it.
 * Tools without an override use the Java-side defaults.
 */
public record McpCapabilitiesConfig(
    ServerInfo serverInfo,
    String instructions,
    Map<String, Object> capabilities,
    Map<String, ToolOverride> tools
) {

    public McpCapabilitiesConfig {
        Objects.requireNonNull(serverInfo, "serverInfo must not be null");
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
        tools = tools == null ? Map.of() : Map.copyOf(tools);
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

    /**
     * YAML override applied on top of a tool's Java-defined definition.
     * Fields are nullable; null means "fall back to the Java default".
     */
    public record ToolOverride(String description) {
    }
}
