package io.searchable.example.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Minimal JSON-RPC 2.0 wire types.
 *
 * <p>MCP uses JSON-RPC 2.0 over stdio with newline-delimited frames.
 */
public final class JsonRpcMessage {

    public static final String JSONRPC_VERSION = "2.0";

    private JsonRpcMessage() { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(String jsonrpc, JsonNode id, String method, JsonNode params) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(String jsonrpc, JsonNode id, Object result, Error error) {

        public static Response success(final JsonNode id, final Object result) {
            return new Response(JSONRPC_VERSION, id, result, null);
        }

        public static Response failure(final JsonNode id, final Error error) {
            return new Response(JSONRPC_VERSION, id, null, error);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(int code, String message, Object data) {

        public static Error of(final int code, final String message) {
            return new Error(code, message, null);
        }

        // Standard JSON-RPC error codes
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;
    }
}
