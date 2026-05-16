package io.searchable.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.example.mcp.protocol.ToolDefinition;
import io.searchable.example.mcp.protocol.ToolResult;
import io.searchable.example.mcp.tool.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTest {

    private ObjectMapper json;
    private McpServer server;
    private RecordingTool tool;

    @BeforeEach
    void setUp() {
        json = SearchableMcpApplication.newObjectMapper();
        tool = new RecordingTool(json);
        server = new McpServer(json, List.of(tool));
    }

    private JsonNode roundTrip(final String request) throws Exception {
        final StringWriter sink = new StringWriter();
        try (PrintWriter out = new PrintWriter(sink)) {
            server.handleLine(request, out);
        }
        return json.readTree(sink.toString().trim());
    }

    @Test
    void initializeReturnsServerInfoAndCapabilities() throws Exception {
        final JsonNode response = roundTrip(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

        assertThat(response.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.get("id").asInt()).isEqualTo(1);
        assertThat(response.get("result").get("protocolVersion").asText()).isNotBlank();
        assertThat(response.get("result").get("serverInfo").get("name").asText())
            .isEqualTo("searchable-mcp");
        assertThat(response.get("result").get("capabilities").has("tools")).isTrue();
    }

    @Test
    void toolsListReturnsRegisteredTool() throws Exception {
        final JsonNode response = roundTrip(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");

        final JsonNode tools = response.get("result").get("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("echo");
    }

    @Test
    void toolsCallInvokesRegisteredTool() throws Exception {
        final JsonNode response = roundTrip(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"hello\"}}}");

        final JsonNode content = response.get("result").get("content");
        assertThat(content.get(0).get("text").asText()).isEqualTo("echoed: hello");
    }

    @Test
    void unknownMethodReturnsError() throws Exception {
        final JsonNode response = roundTrip(
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"does/not/exist\"}");

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
        assertThat(response.get("error").get("message").asText()).contains("Method not found");
    }

    @Test
    void unknownToolReturnsError() throws Exception {
        final JsonNode response = roundTrip(
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"unknown\",\"arguments\":{}}}");
        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void notificationDoesNotProduceResponse() throws Exception {
        final StringWriter sink = new StringWriter();
        try (PrintWriter out = new PrintWriter(sink)) {
            server.handleLine(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", out);
        }
        assertThat(sink.toString()).isEmpty();
    }

    @Test
    void stdioLoopProcessesMultipleFrames() throws Exception {
        final String input =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}\n"
            + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}\n";
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        server.serve(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);

        final String[] lines = output.toString(StandardCharsets.UTF_8).trim().split("\\R");
        assertThat(lines).hasSize(2);
        assertThat(json.readTree(lines[0]).get("id").asInt()).isEqualTo(1);
        assertThat(json.readTree(lines[1]).get("id").asInt()).isEqualTo(2);
    }

    private static final class RecordingTool implements McpTool {
        private final ObjectMapper json;

        RecordingTool(final ObjectMapper json) {
            this.json = json;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("echo", "echo the text",
                json.valueToTree(Map.of(
                    "type", "object",
                    "properties", Map.of("text", Map.of("type", "string")),
                    "required", List.of("text"))));
        }

        @Override
        public ToolResult execute(final JsonNode arguments) {
            return ToolResult.text("echoed: " + arguments.get("text").asText());
        }
    }
}
