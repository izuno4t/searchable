package io.searchable.ai.openai;

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
 * {@link AiProvider} implementation backed by the OpenAI Chat Completions
 * API (<a href="https://platform.openai.com/docs/api-reference/chat">docs</a>).
 *
 * <p>The base URL and API key are taken from system properties and
 * environment variables in the following order:
 *
 * <ul>
 *   <li>System property {@code searchable.ai.openai.base-url} /
 *       {@code searchable.ai.openai.api-key}</li>
 *   <li>Environment variable {@code OPENAI_BASE_URL} /
 *       {@code OPENAI_API_KEY}</li>
 *   <li>Default base URL {@code https://api.openai.com/v1}; no default
 *       API key (a missing key produces an {@link AiException.Kind#AUTH}
 *       at {@link #summarize(AiRequest)} call time, not at construction
 *       time)</li>
 * </ul>
 *
 * <p>The default model is {@code gpt-4o-mini}; callers may override per
 * request via {@link AiRequest#model()}.
 *
 * <p>Discovered via {@link java.util.ServiceLoader} through the entry in
 * {@code META-INF/services/io.searchable.ai.AiProvider}.
 */
public final class OpenAiProvider implements AiProvider {

    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    static final String DEFAULT_MODEL = "gpt-4o-mini";
    static final String NAME = "openai";

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /** ServiceLoader entry point — reads system properties / env vars. */
    public OpenAiProvider() {
        this(resolveBaseUrl(), resolveApiKey(), HttpProviderSupport.newClient(null));
    }

    /** Test-friendly constructor allowing injection of HTTP client / endpoint. */
    OpenAiProvider(final String baseUrl, final String apiKey, final HttpClient httpClient) {
        this.baseUrl = Objects.requireNonNullElse(baseUrl, DEFAULT_BASE_URL);
        this.apiKey = apiKey;
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
                "OpenAI API key not configured (set OPENAI_API_KEY or "
                    + "searchable.ai.openai.api-key)");
        }

        final String model = request.model() == null ? DEFAULT_MODEL : request.model();
        final String body;
        try {
            body = mapper.writeValueAsString(buildPayload(request, model));
        } catch (final IOException e) {
            throw new AiException(AiException.Kind.REQUEST,
                "failed to serialize OpenAI request body", e);
        }

        final HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + "/chat/completions"))
                .timeout(request.timeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (final URISyntaxException e) {
            throw new AiException(AiException.Kind.REQUEST,
                "invalid OpenAI base URL: " + baseUrl, e);
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
        final ArrayNode messages = root.putArray("messages");

        final String systemPrompt = request.systemPrompt() == null
            ? defaultSystemPrompt()
            : request.systemPrompt();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", buildUserContent(request)));
        return root;
    }

    private ObjectNode message(final String role, final String content) {
        final ObjectNode node = mapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
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
            final JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiException(AiException.Kind.UPSTREAM,
                    "OpenAI response did not contain any choices: " + body);
            }
            final JsonNode message = choices.get(0).path("message");
            final String text = message.path("content").asText("");
            final String model = root.path("model").asText(
                request.model() == null ? DEFAULT_MODEL : request.model());

            final List<String> citations = extractCitations(text, request);

            final Map<String, Object> usage = new LinkedHashMap<>();
            final JsonNode usageNode = root.path("usage");
            if (usageNode.isObject()) {
                usageNode.fields().forEachRemaining(e -> usage.put(e.getKey(),
                    e.getValue().isNumber() ? e.getValue().numberValue() : e.getValue().asText()));
            }
            return new AiResponse(text, model, citations, usage);
        } catch (final IOException e) {
            throw new AiException(AiException.Kind.UPSTREAM,
                "failed to parse OpenAI response: " + e.getMessage(), e);
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
        final String prop = System.getProperty("searchable.ai.openai.base-url");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        final String env = System.getenv("OPENAI_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return DEFAULT_BASE_URL;
    }

    private static String resolveApiKey() {
        final String prop = System.getProperty("searchable.ai.openai.api-key");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        final String env = System.getenv("OPENAI_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "OpenAiProvider[baseUrl=%s, apiKeyConfigured=%s]",
            baseUrl, apiKey != null);
    }
}
