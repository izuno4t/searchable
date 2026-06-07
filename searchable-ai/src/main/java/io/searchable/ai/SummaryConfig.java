package io.searchable.ai;

import java.time.Duration;
import java.util.Objects;

/**
 * Tunables for {@link SummaryService}.
 *
 * @param providerName     identifier of the {@link AiProvider} to invoke
 *                         (e.g. {@code "openai"}, {@code "anthropic"},
 *                         {@code "ollama"}); {@code null}/blank disables
 *                         summarisation
 * @param model            model name to request; {@code null} lets the
 *                         provider choose its default
 * @param timeout          maximum wall-clock duration for a single
 *                         {@link AiProvider#summarize(AiRequest)} call
 * @param maxTokens        upper bound passed to the provider as
 *                         {@code max_tokens} / equivalent
 * @param temperature      sampling temperature in {@code [0.0, 2.0]}
 * @param maxContextItems  cap on number of search hits forwarded as context
 * @param maxContextChars  cap on total character length of context body
 *                         (excess items are dropped, never truncated mid-doc
 *                         so citations remain valid)
 * @param fallbackOnError  when {@code true}, {@link AiException} of kind
 *                         {@link AiException.Kind#TIMEOUT} or
 *                         {@link AiException.Kind#UPSTREAM} is converted to
 *                         {@link SummaryService#fallbackResponse(AiException)}
 *                         rather than rethrown. {@link AiException.Kind#AUTH}
 *                         and {@link AiException.Kind#REQUEST} are always
 *                         rethrown so misconfiguration surfaces loudly
 */
public record SummaryConfig(
    String providerName,
    String model,
    Duration timeout,
    int maxTokens,
    double temperature,
    int maxContextItems,
    int maxContextChars,
    boolean fallbackOnError
) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    public static final int DEFAULT_MAX_TOKENS = 512;
    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_MAX_CONTEXT_ITEMS = 5;
    public static final int DEFAULT_MAX_CONTEXT_CHARS = 8000;

    public SummaryConfig {
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxTokens <= 0) {
            maxTokens = DEFAULT_MAX_TOKENS;
        }
        if (maxContextItems <= 0) {
            maxContextItems = DEFAULT_MAX_CONTEXT_ITEMS;
        }
        if (maxContextChars <= 0) {
            maxContextChars = DEFAULT_MAX_CONTEXT_CHARS;
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException(
                "temperature must be within [0.0, 2.0], got " + temperature);
        }
    }

    /** AI summarisation disabled (provider name null). */
    public static SummaryConfig disabled() {
        return new SummaryConfig(null, null, DEFAULT_TIMEOUT, DEFAULT_MAX_TOKENS,
            DEFAULT_TEMPERATURE, DEFAULT_MAX_CONTEXT_ITEMS,
            DEFAULT_MAX_CONTEXT_CHARS, true);
    }

    /** Convenient minimal config selecting a provider with all other defaults. */
    public static SummaryConfig forProvider(final String providerName) {
        Objects.requireNonNull(providerName, "providerName");
        return new SummaryConfig(providerName, null, DEFAULT_TIMEOUT,
            DEFAULT_MAX_TOKENS, DEFAULT_TEMPERATURE,
            DEFAULT_MAX_CONTEXT_ITEMS, DEFAULT_MAX_CONTEXT_CHARS, true);
    }

    public boolean enabled() {
        return providerName != null && !providerName.isBlank();
    }
}
