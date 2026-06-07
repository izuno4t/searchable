package io.searchable.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchResult;

/**
 * Glue layer that converts a search query + matching hits into an
 * {@link AiRequest}, dispatches it through the configured
 * {@link AiProvider}, and returns the resulting {@link AiResponse}.
 *
 * <p>Two entry points are provided:
 *
 * <ul>
 *   <li>{@link #summarize(String, SearchResult)} — turns a domain
 *       {@link SearchResult} into context items automatically.</li>
 *   <li>{@link #summarize(String, List)} — accepts an arbitrary list of
 *       pre-built {@link AiContextItem context items} for callers that need
 *       custom mapping.</li>
 * </ul>
 *
 * <p>Behaviour when summarisation is disabled, no provider is registered for
 * {@link SummaryConfig#providerName()}, or context exhaustion strips all
 * hits:
 *
 * <ul>
 *   <li>{@link #summarize(String, SearchResult)} returns
 *       {@link #disabledResponse()} so callers can render the raw search
 *       result unmodified.</li>
 *   <li>{@link AiException.Kind#TIMEOUT} / {@link AiException.Kind#UPSTREAM}
 *       failures are converted to {@link #fallbackResponse(AiException)}
 *       when {@link SummaryConfig#fallbackOnError()} is {@code true}; AUTH
 *       and REQUEST failures are always rethrown so misconfiguration
 *       surfaces.</li>
 * </ul>
 */
public final class SummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryService.class);
    private static final String DISABLED_MARKER = "ai-disabled";
    private static final String FALLBACK_MARKER = "ai-fallback";

    private final AiProviderRegistry registry;
    private final SummaryConfigProvider configProvider;

    /** Construct with an immutable config. Useful for tests / one-shot wiring. */
    public SummaryService(final AiProviderRegistry registry, final SummaryConfig config) {
        this(registry, new SummaryConfigProvider(Objects.requireNonNull(config, "config")));
    }

    /** Construct with a mutable holder so the active config can change at runtime. */
    public SummaryService(final AiProviderRegistry registry,
                          final SummaryConfigProvider configProvider) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.configProvider = Objects.requireNonNull(configProvider, "configProvider");
    }

    /** Summarise the top hits of {@code searchResult} for the given user query. */
    public AiResponse summarize(final String query, final SearchResult searchResult)
            throws AiException {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(searchResult, "searchResult");
        return summarize(query, toContextItems(searchResult));
    }

    /** Summarise the supplied context items for the given user query. */
    public AiResponse summarize(final String query, final List<AiContextItem> context)
            throws AiException {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(context, "context");

        final SummaryConfig config = configProvider.current();
        if (!config.enabled()) {
            LOG.debug("Summarisation disabled (config.providerName=null)");
            return disabledResponse();
        }

        final AiProvider provider = registry.get(config.providerName()).orElse(null);
        if (provider == null) {
            LOG.warn("Configured AI provider '{}' is not registered; falling back",
                config.providerName());
            return fallbackResponse(new AiException(AiException.Kind.REQUEST,
                "provider not registered: " + config.providerName()));
        }

        final List<AiContextItem> limited = limitContext(context, config);
        final AiRequest request = AiRequest.builder()
            .query(query)
            .context(limited)
            .model(config.model())
            .maxTokens(config.maxTokens())
            .temperature(config.temperature())
            .timeout(config.timeout())
            .build();

        try {
            return provider.summarize(request);
        } catch (final AiException e) {
            if (shouldFallback(e, config)) {
                LOG.warn("AI summarisation failed ({}), falling back: {}",
                    e.kind(), e.getMessage());
                return fallbackResponse(e);
            }
            throw e;
        }
    }

    /** Map a {@link SearchResult} to AI context items (capped by config). */
    public List<AiContextItem> toContextItems(final SearchResult result) {
        Objects.requireNonNull(result, "result");
        final List<AiContextItem> items = new ArrayList<>();
        for (final SearchHit hit : result.hits()) {
            items.add(toContextItem(hit));
        }
        return items;
    }

    static AiContextItem toContextItem(final SearchHit hit) {
        final Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("namespace", hit.namespaceId());
        meta.put("score", hit.score());
        for (final Map.Entry<String, Object> e : hit.metadata().entrySet()) {
            meta.putIfAbsent(e.getKey(), e.getValue());
        }
        final String text = hit.content() == null ? "" : hit.content();
        return new AiContextItem(hit.documentId(), hit.title(), text, meta);
    }

    private static List<AiContextItem> limitContext(final List<AiContextItem> input,
                                                    final SummaryConfig config) {
        if (input.isEmpty()) {
            return List.of();
        }
        final List<AiContextItem> out = new ArrayList<>();
        int chars = 0;
        for (final AiContextItem item : input) {
            if (out.size() >= config.maxContextItems()) {
                break;
            }
            final int len = item.text().length();
            if (!out.isEmpty() && chars + len > config.maxContextChars()) {
                break;
            }
            out.add(item);
            chars += len;
        }
        return out;
    }

    private static boolean shouldFallback(final AiException e, final SummaryConfig config) {
        if (!config.fallbackOnError()) {
            return false;
        }
        return e.kind() == AiException.Kind.TIMEOUT
            || e.kind() == AiException.Kind.UPSTREAM
            || e.kind() == AiException.Kind.UNKNOWN;
    }

    /**
     * Sentinel response used when summarisation is disabled. The
     * {@link AiResponse#text()} is empty and {@link AiResponse#model()} is
     * {@code "ai-disabled"} so callers can detect and render the raw search
     * result.
     */
    public static AiResponse disabledResponse() {
        return new AiResponse("", DISABLED_MARKER, List.of(), Map.of());
    }

    /**
     * Sentinel response used when the provider call failed and
     * {@link SummaryConfig#fallbackOnError()} is {@code true}. Empty
     * {@link AiResponse#text()}; {@link AiResponse#model()} is
     * {@code "ai-fallback"}; the {@link AiResponse#usage()} map carries
     * {@code error.kind} and {@code error.message} so callers can surface
     * a diagnostic.
     */
    public static AiResponse fallbackResponse(final AiException cause) {
        final Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("error.kind", cause.kind().name());
        usage.put("error.message", cause.getMessage() == null ? "" : cause.getMessage());
        return new AiResponse("", FALLBACK_MARKER, List.of(), usage);
    }

    /** Indicates whether the given response is a {@link #disabledResponse()}. */
    public static boolean isDisabled(final AiResponse response) {
        return response != null && DISABLED_MARKER.equals(response.model());
    }

    /** Indicates whether the given response is a {@link #fallbackResponse(AiException)}. */
    public static boolean isFallback(final AiResponse response) {
        return response != null && FALLBACK_MARKER.equals(response.model());
    }
}
