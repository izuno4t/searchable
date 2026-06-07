package io.searchable.ai.anthropic;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.searchable.ai.AiContextItem;
import io.searchable.ai.AiException;
import io.searchable.ai.AiProvider;
import io.searchable.ai.AiRequest;
import io.searchable.ai.AiResponse;
import io.searchable.ai.internal.HttpProviderSupport;

/**
 * {@link AiProvider} implementation backed by the Anthropic Messages API
 * (<a href="https://docs.anthropic.com/en/api/messages">docs</a>).
 *
 * <p>Configuration is resolved in the following order:
 *
 * <ul>
 *   <li>System property {@code searchable.ai.anthropic.base-url} /
 *       {@code searchable.ai.anthropic.api-key} /
 *       {@code searchable.ai.anthropic.api-version}</li>
 *   <li>Environment variable {@code ANTHROPIC_BASE_URL} /
 *       {@code ANTHROPIC_API_KEY} / {@code ANTHROPIC_API_VERSION}</li>
 *   <li>Defaults: base URL {@code https://api.anthropic.com},
 *       API version {@code 2023-06-01}, no default API key</li>
 * </ul>
 *
 * <p>The default model is {@code claude-sonnet-4-6} (Claude Sonnet 4.6,
 * the current latest non-Opus tier); callers may override per request
 * via {@link AiRequest#model()}.
 *
 * <p>Discovered via {@link java.util.ServiceLoader} through the entry in
 * {@code META-INF/services/io.searchable.ai.AiProvider}.
 */
public final class AnthropicProvider implements AiProvider {

    static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    static final String DEFAULT_API_VERSION = "2023-06-01";
    static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    static final String NAME = "anthropic";

    private final String baseUrl;
    private final String apiKey;
    private final String apiVersion;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /** ServiceLoader entry point — reads system properties / env vars. */
    public AnthropicProvider() {
        this(resolveBaseUrl(), resolveApiKey(), resolveApiVersion(),
            HttpProviderSupport.newClient(null));
    }

    /** Test-friendly constructor. */
    AnthropicProvider(final String baseUrl, final String apiKey,
                      final String apiVersion, final HttpClient httpClient) {
        this.baseUrl = Objects.requireNonNullElse(baseUrl, DEFAULT_BASE_URL);
        this.apiKey = apiKey;
        this.apiVersion = Objects.requireNonNullElse(apiVersion, DEFAULT_API_VERSION);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AiResponse summarize(final AiRequest request) throws AiException {
        Objects.requireNonNull(request, "request");
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiException(AiException.Kind.AUTH,
                "Anthropic API key not configured (set ANTHROPIC_API_KEY or "
                    + "searchable.ai.anthropic.api-key)");
        }

        final String model = request.model() == null ? DEFAULT_MODEL : request.model();
        final String body;
        try {
            body = mapper.writeValueAsString(buildPayload(request, model));
        } catch (final IOException e) {
            throw new AiException(AiException.Kind.REQUEST,
                "failed to serialize Anthropic request body", e);
        }

        final HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + "/v1/messages"))
                .timeout(request.timeout())
                .header("x-api-key", apiKey)
                .header("anthropic-version", apiVersion)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (final URISyntaxException e) {
            throw new AiException(AiException.Kind.REQUEST,
                "invalid Anthropic base URL: " + baseUrl, e);
        }

        final HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (final IOException e) {
            throw HttpProviderSupport.wrapIoFailure(NAME, e);
        } catch (final InterruptedException e) {
            throw HttpProviderSupport.wrapInterrupted(NAME, e);
        }

        if (response.statusCode() / 100 != 2) {
            throw HttpProviderSupport.toException(NAME, response);
        }

        return parseResponse(response.body(), request);
    }

    private ObjectNode buildPayload(final AiRequest request, final String model) {
        final ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", request.maxTokens());
        root.put("temperature", request.temperature());
        final String systemPrompt = request.systemPrompt() == null
            ? defaultSystemPrompt()
            : request.systemPrompt();
        root.put("system", systemPrompt);

        final ArrayNode messages = root.putArray("messages");
        final ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", buildUserContent(request));
        messages.add(userMsg);
        return root;
    }

    private static String defaultSystemPrompt() {
        return "You answer the user question using only the provided context. "
            + "Cite source ids in square brackets, e.g. [doc-123], when "
            + "referencing a context item. If the context does not contain "
            + "the answer, say so explicitly.";
    }

    static String buildUserContent(final AiRequest request) {
        final StringBuilder sb = new StringBuilder();
        if (!request.context().isEmpty()) {
            sb.append("Context:\n");
            for (final AiContextItem item : request.context()) {
                sb.append('[').append(item.sourceId()).append("] ")
                    .append(item.title()).append('\n')
                    .append(item.text()).append("\n\n");
            }
        }
        sb.append("Question: ").append(request.query());
        return sb.toString();
    }

    private AiResponse parseResponse(final String body, final AiRequest request) throws AiException {
        try {
            final JsonNode root = mapper.readTree(body);
            final JsonNode content = root.path("content");
            if (!content.isArray() || content.isEmpty()) {
                throw new AiException(AiException.Kind.UPSTREAM,
                    "Anthropic response did not contain any content blocks: " + body);
            }
            final StringBuilder text = new StringBuilder();
            for (final JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText(""));
                }
            }
            final String model = root.path("model").asText(
                request.model() == null ? DEFAULT_MODEL : request.model());

            final List<String> citations = extractCitations(text.toString(), request);

            final Map<String, Object> usage = new LinkedHashMap<>();
            final JsonNode usageNode = root.path("usage");
            if (usageNode.isObject()) {
                usageNode.fields().forEachRemaining(e -> usage.put(e.getKey(),
                    e.getValue().isNumber() ? e.getValue().numberValue() : e.getValue().asText()));
            }
            return new AiResponse(text.toString(), model, citations, usage);
        } catch (final IOException e) {
            throw new AiException(AiException.Kind.UPSTREAM,
                "failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }

    static List<String> extractCitations(final String text, final AiRequest request) {
        final List<String> hits = new ArrayList<>();
        for (final AiContextItem item : request.context()) {
            final String marker = "[" + item.sourceId() + "]";
            if (text.contains(marker) && !hits.contains(item.sourceId())) {
                hits.add(item.sourceId());
            }
        }
        return hits;
    }

    private static String resolveBaseUrl() {
        final String prop = System.getProperty("searchable.ai.anthropic.base-url");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        final String env = System.getenv("ANTHROPIC_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return DEFAULT_BASE_URL;
    }

    private static String resolveApiKey() {
        final String prop = System.getProperty("searchable.ai.anthropic.api-key");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        final String env = System.getenv("ANTHROPIC_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return null;
    }

    private static String resolveApiVersion() {
        final String prop = System.getProperty("searchable.ai.anthropic.api-version");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        final String env = System.getenv("ANTHROPIC_API_VERSION");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return DEFAULT_API_VERSION;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "AnthropicProvider[baseUrl=%s, apiVersion=%s, apiKeyConfigured=%s]",
            baseUrl, apiVersion, apiKey != null);
    }
}
