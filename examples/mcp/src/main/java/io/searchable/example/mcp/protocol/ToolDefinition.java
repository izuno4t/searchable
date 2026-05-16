package io.searchable.example.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP tool advertisement returned by {@code tools/list}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolDefinition(String name, String description, JsonNode inputSchema) { }
