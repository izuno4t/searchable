package io.searchable.ai.testfixture;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal HTTP server used by provider tests to simulate the OpenAI /
 * Anthropic / Ollama endpoints without a network round-trip.
 *
 * <p>Each test installs a {@link CannedResponse} and inspects the
 * {@link Recorded recorded request} after the call returns.
 *
 * <p>Auto-closeable: callers should wrap usage in try-with-resources so the
 * port is released even on assertion failure.
 */
public final class FakeHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<CannedResponse> nextResponse = new AtomicReference<>(
        new CannedResponse(200, "application/json", "{}"));
    private final List<Recorded> recorded = new ArrayList<>();

    public FakeHttpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new Handler());
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void setNext(final CannedResponse response) {
        this.nextResponse.set(response);
    }

    public Recorded lastRecorded() {
        if (recorded.isEmpty()) {
            throw new IllegalStateException("no recorded requests");
        }
        return recorded.get(recorded.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private final class Handler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final byte[] body = exchange.getRequestBody().readAllBytes();
            // Normalise header names to lower-case so test assertions can use
            // a single canonical form regardless of how the server framework
            // stored them on the wire (HttpServer uses title-case keys).
            final Map<String, List<String>> normalisedHeaders = new java.util.LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) ->
                normalisedHeaders.put(k.toLowerCase(java.util.Locale.ROOT), List.copyOf(v)));
            recorded.add(new Recorded(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                Map.copyOf(normalisedHeaders),
                new String(body, StandardCharsets.UTF_8)));

            final CannedResponse next = nextResponse.get();
            final byte[] payload = next.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", next.contentType());
            exchange.sendResponseHeaders(next.status(), payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /** A pre-baked HTTP response. */
    public record CannedResponse(int status, String contentType, String body) {
        public static CannedResponse ok(final String json) {
            return new CannedResponse(200, "application/json", json);
        }

        public static CannedResponse error(final int status, final String body) {
            return new CannedResponse(status, "application/json", body);
        }
    }

    /** A captured request. */
    public record Recorded(String method, String path,
                           Map<String, List<String>> headers,
                           String body) {
    }
}
