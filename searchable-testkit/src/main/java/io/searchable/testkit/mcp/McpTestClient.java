package io.searchable.testkit.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Lightweight client for the stdio-based MCP server used in integration tests.
 *
 * <p>Bridges a single JSON-RPC request through a {@code (in, out)} pair as
 * exposed by {@code McpServer#serve(InputStream, OutputStream)}. Hides the
 * {@code jsonrpc/id} bookkeeping so test code can focus on method and params.
 */
public final class McpTestClient {

    private final ObjectMapper json;
    private final BiConsumer<ByteArrayInputStream, ByteArrayOutputStream> serve;
    private final AtomicLong nextId = new AtomicLong();

    /**
     * @param json the {@link ObjectMapper} the server uses (typically
     *             {@code SearchableMcpApplication.newObjectMapper()})
     * @param serve adapter invoking {@code server.serve(in, out)}
     */
    public McpTestClient(final ObjectMapper json,
                         final BiConsumer<ByteArrayInputStream, ByteArrayOutputStream> serve) {
        this.json = json;
        this.serve = serve;
    }

    /** Send a tools/list request and return the parsed response. */
    public JsonNode toolsList() throws IOException {
        return send("tools/list", null);
    }

    /** Send a tools/call request with the given arguments. */
    public JsonNode toolsCall(final String name, final ObjectNode arguments) throws IOException {
        final ObjectNode params = json.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return send("tools/call", params);
    }

    /** Generic JSON-RPC dispatch. */
    public JsonNode send(final String method, final ObjectNode params) throws IOException {
        final ObjectNode request = json.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", nextId.incrementAndGet());
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }
        final ByteArrayInputStream in = new ByteArrayInputStream(
            (request + "\n").getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        serve.accept(in, out);
        return json.readTree(out.toString(StandardCharsets.UTF_8).trim());
    }
}
