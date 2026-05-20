package io.searchable.example.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.example.mcp.McpServer;
import io.searchable.example.mcp.config.McpCapabilitiesConfig;
import io.searchable.example.mcp.protocol.JsonRpcMessage;
import io.searchable.example.mcp.protocol.ToolDefinition;
import io.searchable.example.mcp.protocol.ToolResult;
import io.searchable.example.mcp.tool.McpTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SseTransportTest {

    private SseTransport transport;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        final ObjectMapper json = new ObjectMapper();
        final McpServer server = new McpServer(json, List.of(new HelloTool(json)), capabilities());
        transport = new SseTransport(server, json, null);
        transport.start(port);
    }

    @AfterEach
    void tearDown() {
        transport.stop();
    }

    @Test
    void sseEndpointEmitsEndpointEvent() throws Exception {
        final AtomicReference<String> sessionUrl = new AtomicReference<>();
        final Thread reader = new Thread(() -> {
            try {
                final HttpURLConnection conn = (HttpURLConnection)
                    URI.create("http://localhost:" + port + "/sse").toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setReadTimeout(2000);
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: /messages?session=")) {
                            sessionUrl.set(line.substring("data: ".length()));
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Test will fail via assertion below.
            }
        }, "sse-reader");
        reader.start();
        reader.join(3000);
        assertThat(sessionUrl.get()).startsWith("/messages?session=");
    }

    @Test
    void apiKeyEnforcedWhenConfigured() throws Exception {
        transport.stop();
        transport = new SseTransport(
            new McpServer(new ObjectMapper(), List.of(new HelloTool(new ObjectMapper())),
                capabilities()),
            new ObjectMapper(), "secret");
        transport.start(port);
        final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

        final HttpResponse<String> denied = http.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/sse"))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(401);
    }

    private static McpCapabilitiesConfig capabilities() {
        return new McpCapabilitiesConfig(
            new McpCapabilitiesConfig.ServerInfo("searchable-mcp", "1.0.0"),
            Map.of("tools", Map.of()));
    }

    /** Trivial tool used to exercise the transport without depending on Lucene. */
    private static final class HelloTool implements McpTool {
        private final ObjectMapper json;
        HelloTool(final ObjectMapper json) { this.json = json; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition("hello", "echo", json.createObjectNode());
        }
        @Override public ToolResult execute(final com.fasterxml.jackson.databind.JsonNode args) {
            return ToolResult.text("hi");
        }
    }

    // Reference unused class to keep classpath happy in IDEs.
    @SuppressWarnings("unused")
    private void touchProtocol(final JsonRpcMessage.Request r) { }
}
