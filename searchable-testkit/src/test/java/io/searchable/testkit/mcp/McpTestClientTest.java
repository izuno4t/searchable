package io.searchable.testkit.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class McpTestClientTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void toolsListSendsMethodAndAssignsAutoIncrementingIds() throws Exception {
        final EchoServer echo = new EchoServer(json);
        final McpTestClient client = new McpTestClient(json, echo);

        final JsonNode first = client.toolsList();
        final JsonNode second = client.toolsList();

        assertThat(first.get("echo").get("method").asText()).isEqualTo("tools/list");
        assertThat(first.get("echo").has("params")).isFalse();
        assertThat(first.get("echo").get("id").asLong()).isEqualTo(1L);
        assertThat(second.get("echo").get("id").asLong()).isEqualTo(2L);
        assertThat(first.get("echo").get("jsonrpc").asText()).isEqualTo("2.0");
    }

    @Test
    void toolsCallWrapsArgumentsUnderParams() throws Exception {
        final EchoServer echo = new EchoServer(json);
        final McpTestClient client = new McpTestClient(json, echo);

        final ObjectNode args = json.createObjectNode();
        args.put("query", "hello");
        final JsonNode response = client.toolsCall("search", args);

        final JsonNode params = response.get("echo").get("params");
        assertThat(response.get("echo").get("method").asText()).isEqualTo("tools/call");
        assertThat(params.get("name").asText()).isEqualTo("search");
        assertThat(params.get("arguments").get("query").asText()).isEqualTo("hello");
    }

    @Test
    void sendOmitsParamsWhenNull() throws Exception {
        final EchoServer echo = new EchoServer(json);
        final McpTestClient client = new McpTestClient(json, echo);

        final JsonNode response = client.send("ping", null);

        assertThat(response.get("echo").get("method").asText()).isEqualTo("ping");
        assertThat(response.get("echo").has("params")).isFalse();
    }

    /** Minimal stand-in for an MCP server: parses the request and echoes it back. */
    private static final class EchoServer implements BiConsumer<ByteArrayInputStream, ByteArrayOutputStream> {
        private final ObjectMapper json;

        EchoServer(final ObjectMapper json) {
            this.json = json;
        }

        @Override
        public void accept(final ByteArrayInputStream in, final ByteArrayOutputStream out) {
            try {
                final JsonNode req = json.readTree(in);
                final ObjectNode response = json.createObjectNode();
                response.set("echo", req);
                out.write(json.writeValueAsBytes(response));
                out.write('\n');
                out.flush();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void responsesAreParsedAsUtf8() throws Exception {
        final BiConsumer<ByteArrayInputStream, ByteArrayOutputStream> fixed = (in, out) -> {
            try {
                out.write("{\"result\":\"こんにちは\"}\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        final McpTestClient client = new McpTestClient(json, fixed);

        final JsonNode resp = client.send("anything", null);
        assertThat(resp.get("result").asText()).isEqualTo("こんにちは");
    }
}
