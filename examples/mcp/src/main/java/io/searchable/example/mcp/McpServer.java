package io.searchable.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.searchable.example.mcp.config.McpCapabilitiesConfig;
import io.searchable.example.mcp.protocol.JsonRpcMessage;
import io.searchable.example.mcp.protocol.ToolDefinition;
import io.searchable.example.mcp.protocol.ToolResult;
import io.searchable.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal MCP server using newline-delimited JSON-RPC 2.0 over stdio.
 *
 * <p>Supports the handshake ({@code initialize}, {@code
 * notifications/initialized}), tool discovery ({@code tools/list}), tool
 * invocation ({@code tools/call}), and health checks ({@code ping}).
 */
public final class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper json;
    private final Map<String, McpTool> tools;
    private final McpCapabilitiesConfig capabilities;

    public McpServer(
        final ObjectMapper json,
        final List<McpTool> tools,
        final McpCapabilitiesConfig capabilities) {
        this.json = Objects.requireNonNull(json);
        this.capabilities = Objects.requireNonNull(capabilities);
        this.tools = new LinkedHashMap<>();
        for (final McpTool t : tools) {
            this.tools.put(t.definition().name(), t);
        }
    }

    /** Run the read/dispatch loop over the given streams until EOF. */
    public void serve(final InputStream input, final OutputStream output) throws IOException {
        final BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8));
        final Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        final PrintWriter out = new PrintWriter(writer, true);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            handleLine(line, out);
        }
        log.info("MCP stdio loop ended (EOF)");
    }

    public void handleLine(final String line, final PrintWriter out) {
        final JsonRpcMessage.Request req;
        try {
            req = json.readValue(line, JsonRpcMessage.Request.class);
        } catch (Exception e) {
            log.warn("failed to parse JSON-RPC frame: {}", e.getMessage());
            write(out, JsonRpcMessage.Response.failure(null, JsonRpcMessage.Error.of(
                JsonRpcMessage.Error.PARSE_ERROR, "Parse error: " + e.getMessage())));
            return;
        }

        if (req.id() == null) {
            handleNotification(req);
            return;
        }

        final JsonRpcMessage.Response response = dispatch(req);
        if (response != null) {
            write(out, response);
        }
    }

    private void handleNotification(final JsonRpcMessage.Request req) {
        log.debug("notification: {}", req.method());
    }

    private JsonRpcMessage.Response dispatch(final JsonRpcMessage.Request req) {
        try {
            return switch (req.method()) {
                case "initialize" -> JsonRpcMessage.Response.success(req.id(), initializeResult());
                case "ping" -> JsonRpcMessage.Response.success(req.id(), Map.of());
                case "tools/list" -> JsonRpcMessage.Response.success(req.id(),
                    Map.of("tools", toolList()));
                case "tools/call" -> handleToolsCall(req);
                default -> JsonRpcMessage.Response.failure(req.id(), JsonRpcMessage.Error.of(
                    JsonRpcMessage.Error.METHOD_NOT_FOUND, "Method not found: " + req.method()));
            };
        } catch (RuntimeException e) {
            log.warn("dispatch error for {}", req.method(), e);
            return JsonRpcMessage.Response.failure(req.id(), JsonRpcMessage.Error.of(
                JsonRpcMessage.Error.INTERNAL_ERROR, e.getMessage()));
        }
    }

    private Map<String, Object> initializeResult() {
        return Map.of(
            "protocolVersion", PROTOCOL_VERSION,
            "capabilities", capabilities.capabilities(),
            "serverInfo", Map.of(
                "name", capabilities.serverInfo().name(),
                "version", capabilities.serverInfo().version())
        );
    }

    private List<ToolDefinition> toolList() {
        return tools.values().stream().map(McpTool::definition).toList();
    }

    private JsonRpcMessage.Response handleToolsCall(final JsonRpcMessage.Request req) {
        final JsonNode params = req.params();
        if (params == null || !params.hasNonNull("name")) {
            return JsonRpcMessage.Response.failure(req.id(), JsonRpcMessage.Error.of(
                JsonRpcMessage.Error.INVALID_PARAMS, "'name' is required"));
        }
        final String name = params.get("name").asText();
        final McpTool tool = tools.get(name);
        if (tool == null) {
            return JsonRpcMessage.Response.failure(req.id(), JsonRpcMessage.Error.of(
                JsonRpcMessage.Error.METHOD_NOT_FOUND, "Unknown tool: " + name));
        }
        final JsonNode arguments = params.get("arguments");
        final ToolResult result = tool.execute(arguments);
        return JsonRpcMessage.Response.success(req.id(), result);
    }

    private void write(final PrintWriter out, final JsonRpcMessage.Response response) {
        try {
            final ObjectNode node = json.valueToTree(response);
            out.println(node.toString());
            out.flush();
        } catch (Exception e) {
            log.error("failed to serialize response", e);
        }
    }
}
