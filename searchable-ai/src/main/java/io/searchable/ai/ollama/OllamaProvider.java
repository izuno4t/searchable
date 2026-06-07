package io.searchable.ai.ollama;

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
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.searchable.ai.AiContextItem;
import io.searchable.ai.AiException;
import io.searchable.ai.AiProvider;
import io.searchable.ai.AiRequest;
import io.searchable.ai.AiResponse;
import io.searchable.ai.internal.HttpProviderSupport;

/**
 * {@link AiProvider} implementation backed by a local Ollama server
 * (<a href="https://github.com/ollama/ollama/blob/main/docs/api.md">api docs</a>).
 *
 * <p>Configuration is resolved in the following order:
 *
 * <ul>
 *   <li>System property {@code searchable.ai.ollama.base-url} /
 *       {@code searchable.ai.ollama.default-model}</li>
 *   <li>Environment variable {@code OLLAMA_BASE_URL} /
 *       {@code OLLAMA_DEFAULT_MODEL}</li>
 *   <li>Defaults: base URL {@code http://localhost:11434}, default model
 *       {@code llama3.2}</li>
 * </ul>
 *
 * <p>Ollama is unauthenticated by default; this provider sends no
 * {@code Authorization} header. Use a reverse proxy if you need access
 * control. {@link AiException.Kind#AUTH} is therefore not produced
 * here — only {@code UPSTREAM} / {@code TIMEOUT} / {@code REQUEST} /
 * {@code UNKNOWN}.
 *
 * <p>Discovered via {@link java.util.ServiceLoader} through the entry in
 * {@code META-INF/services/io.searchable.ai.AiProvider}.
 */
public final class OllamaProvider implements AiProvider {

    static final String DEFAULT_BASE_URL = "http://localhost:11434";
    static final String DEFAULT_MODEL = "llama3.2";
    static final String NAME = "ollama";

    private final String baseUrl;
    private final String defaultModel;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /** ServiceLoader entry point — reads system properties / env vars. */
    public OllamaProvider() {
        this(resolveBaseUrl(), resolveDefaultModel(), HttpProviderSupport.newClient(null));
    }

    /** Test-friendly constructor. */
    OllamaProvider(final String baseUrl, final String defaultModel, final HttpClient httpClient) {
        this.baseUrl = Objects.requireNonNullElse(baseUrl, DEFAULT_BASE_URL);
        this.defaultModel = Objects.requireNonNullElse(defaultModel, DEFAULT_MODEL);
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

        final String model = request.model() == null ? defaultModel : request.model();
        final String body;
        try {
            body = mapper.writeValueAsString(buildPayload(request, model));
        } catch (final IOException e) {
            throw new AiException(AiException.Kind.REQUEST,
                "failed to serialize Ollama request body", e);
        }

        final HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + "/api/generate"))
                .timeout(request.timeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (final URISyntaxException e) {
            throw new AiException(AiException.Kind.REQUEST,
                "invalid Ollama base URL: " + baseUrl, e);
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

        return parseResponse(response.body(), request, model);
    }

    private ObjectNode buildPayload(final AiRequest request, final String model) {
        final ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("prompt", buildUserContent(request));
        root.put("stream", false);
        final String systemPrompt = request.systemPrompt() == null
            ? defaultSystemPrompt()
            : request.systemPrompt();
        root.put("system", systemPrompt);

        final ObjectNode options = root.putObject("options");
        options.put("num_predict", request.maxTokens());
        options.put("temperature", request.temperature());
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

    private AiResponse parseResponse(final String body, final AiRequest request,
                                     final String fallbackModel) throws AiException {
        try {
            final JsonNode root = mapper.readTree(body);
            final String text = root.path("response").asText("");
            if (text.isEmpty()) {
                throw new AiException(AiException.Kind.UPSTREAM,
                    "Ollama response did not contain a 'response' field: " + body);
            }
            final String model = root.path("model").asText(fallbackModel);

            final List<String> citations = extractCitations(text, request);

            final Map<String, Object> usage = new LinkedHashMap<>();
            putIfPresent(root, "prompt_eval_count", usage);
            putIfPresent(root, "eval_count", usage);
            putIfPresent(root, "total_duration", usage);
            putIfPresent(root, "load_duration", usage);
            putIfPresent(root, "prompt_eval_duration", usage);
            putIfPresent(root, "eval_duration", usage);
            return new AiResponse(text, model, citations, usage);
        } catch (final IOException e) {
            throw new AiException(AiException.Kind.UPSTREAM,
                "failed to parse Ollama response: " + e.getMessage(), e);
        }
    }

    private static void putIfPresent(final JsonNode root, final String key,
                                     final Map<String, Object> target) {
        final JsonNode node = root.path(key);
        if (node.isNumber()) {
            target.put(key, node.numberValue());
        } else if (!node.isMissingNode() && !node.isNull()) {
            target.put(key, node.asText());
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
        final String prop = System.getProperty("searchable.ai.ollama.base-url");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        final String env = System.getenv("OLLAMA_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return DEFAULT_BASE_URL;
    }

    private static String resolveDefaultModel() {
        final String prop = System.getProperty("searchable.ai.ollama.default-model");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        final String env = System.getenv("OLLAMA_DEFAULT_MODEL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return DEFAULT_MODEL;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "OllamaProvider[baseUrl=%s, defaultModel=%s]", baseUrl, defaultModel);
    }
}
