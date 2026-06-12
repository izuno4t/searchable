package io.searchable.example.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.searchable.example.mcp.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Minimal MCP SSE transport (TASK-142 / TASK-143).
 *
 * <p>Exposes two endpoints over the built-in JDK HTTP server:
 * <ul>
 *   <li>{@code GET /sse} — opens an SSE stream and immediately emits an
 *       {@code endpoint} event pointing at {@code /messages?session=<id>}.</li>
 *   <li>{@code POST /messages?session=<id>} — accepts a JSON-RPC message,
 *       dispatches it through {@link McpServer}, and writes the response
 *       back to the SSE stream associated with {@code session}.</li>
 * </ul>
 *
 * <p>Designed for single-client demos. A production deployment should use
 * a proper async framework (Spring WebFlux, Jetty, etc.). When an API key
 * is configured both endpoints require the {@code X-API-Key} header.
 */
public final class SseTransport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);

    private final McpServer server;
    private final ObjectMapper json;
    private final String apiKey;
    private final ConcurrentHashMap<String, PrintWriter> sessions = new ConcurrentHashMap<>();
    private HttpServer http;

    public SseTransport(final McpServer server, final ObjectMapper json, final String apiKey) {
        this.server = Objects.requireNonNull(server);
        this.json = Objects.requireNonNull(json);
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
    }

    /**
     * Default bind address. Loopback only — exposing the transport on a
     * routable interface requires the explicit {@link #start(String, int)}
     * overload to avoid accidentally publishing an unauthenticated
     * search endpoint on the LAN.
     */
    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";

    /**
     * Start listening on the loopback interface at {@code port}. Equivalent
     * to {@code start(DEFAULT_BIND_ADDRESS, port)}; preserved for callers
     * that should not be exposed off-host. Blocks the calling thread once
     * started.
     */
    public void start(final int port) throws IOException {
        start(DEFAULT_BIND_ADDRESS, port);
    }

    /**
     * Start listening on {@code bindAddress:port}. Pass {@code "0.0.0.0"}
     * (or a routable address) to publish on the LAN — only do that when
     * an API key is configured.
     */
    public void start(final String bindAddress, final int port) throws IOException {
        Objects.requireNonNull(bindAddress, "bindAddress must not be null");
        final InetAddress address = InetAddress.getByName(bindAddress);
        http = HttpServer.create(new InetSocketAddress(address, port), 0);
        http.createContext("/sse", new SseHandler());
        http.createContext("/messages", new MessagesHandler());
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        if (!address.isLoopbackAddress() && apiKey == null) {
            log.warn("MCP SSE bound to {} without an API key — endpoint is "
                + "reachable by anyone who can route to this host", bindAddress);
        }
        log.info("MCP SSE listening on {}:{} (apiKey {})",
            bindAddress, port, apiKey != null ? "required" : "disabled");
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
        }
        sessions.values().forEach(PrintWriter::close);
        sessions.clear();
    }

    /** Visible for tests. */
    int activeSessions() {
        return sessions.size();
    }

    private boolean checkAuth(final HttpExchange exchange) throws IOException {
        if (apiKey == null) {
            return true;
        }
        final var provided = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (constantTimeEquals(apiKey, provided)) {
            return true;
        }
        final byte[] body = "{\"error\":\"INVALID_API_KEY\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(401, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
        return false;
    }

    /**
     * Compare two API keys in time independent of the length of any
     * shared prefix. See the matching helper in
     * {@code ApiKeyFilter} (examples/api).
     */
    private static boolean constantTimeEquals(final String a, final String b) {
        if (a == null || b == null) {
            return false;
        }
        final byte[] x = a.getBytes(StandardCharsets.UTF_8);
        final byte[] y = b.getBytes(StandardCharsets.UTF_8);
        final int len = Math.max(x.length, y.length);
        final byte[] xp = new byte[len];
        final byte[] yp = new byte[len];
        System.arraycopy(x, 0, xp, 0, x.length);
        System.arraycopy(y, 0, yp, 0, y.length);
        return MessageDigest.isEqual(xp, yp) && x.length == y.length;
    }

    private final class SseHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            final String sessionId = java.util.UUID.randomUUID().toString();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            final PrintWriter writer = new PrintWriter(exchange.getResponseBody(), true,
                StandardCharsets.UTF_8);
            writer.println("event: endpoint");
            writer.println("data: /messages?session=" + sessionId);
            writer.println();
            sessions.put(sessionId, writer);
            log.info("SSE session opened: {}", sessionId);

            // Block until the writer fails; the channel closes when the client
            // disconnects, which causes println() to set checkError() to true.
            try {
                while (!writer.checkError()) {
                    Thread.sleep(1000);
                    writer.println(": keepalive");
                    writer.println();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                sessions.remove(sessionId);
                writer.close();
                log.info("SSE session closed: {}", sessionId);
            }
        }
    }

    private final class MessagesHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            final String query = exchange.getRequestURI().getRawQuery();
            final String sessionId = sessionFrom(query);
            final PrintWriter target = sessionId == null ? null : sessions.get(sessionId);
            if (target == null) {
                final byte[] body = "{\"error\":\"NO_SESSION\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
                return;
            }

            final StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                }
            }

            final StringWriter capture = new StringWriter();
            try (PrintWriter capturePw = new PrintWriter(capture, true)) {
                server.handleLine(body.toString().trim(), capturePw);
            }

            final String response = capture.toString().trim();
            if (!response.isEmpty()) {
                synchronized (target) {
                    target.println("event: message");
                    target.println("data: " + response);
                    target.println();
                }
            }
            exchange.sendResponseHeaders(202, -1);
        }

        private String sessionFrom(final String query) {
            if (query == null) return null;
            for (final String part : query.split("&")) {
                final int eq = part.indexOf('=');
                if (eq > 0 && "session".equals(part.substring(0, eq))) {
                    return part.substring(eq + 1);
                }
            }
            return null;
        }
    }
}
