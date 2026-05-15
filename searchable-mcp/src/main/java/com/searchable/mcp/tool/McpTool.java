package com.searchable.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.searchable.mcp.protocol.ToolDefinition;
import com.searchable.mcp.protocol.ToolResult;

/**
 * SPI implemented by every MCP tool the server exposes.
 */
public interface McpTool {

    /** Tool advertisement (name, description, JSON Schema). */
    ToolDefinition definition();

    /** Execute the tool given the JSON-RPC {@code arguments} payload. */
    ToolResult execute(JsonNode arguments);
}
