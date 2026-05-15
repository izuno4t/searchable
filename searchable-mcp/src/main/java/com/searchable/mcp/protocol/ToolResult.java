package com.searchable.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result returned by {@code tools/call}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(List<Content> content, Boolean isError) {

    public static ToolResult text(final String text) {
        return new ToolResult(List.of(new Content("text", text)), null);
    }

    public static ToolResult error(final String text) {
        return new ToolResult(List.of(new Content("text", text)), Boolean.TRUE);
    }

    public record Content(String type, String text) { }
}
