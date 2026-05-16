package io.searchable.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Request envelope for {@link AiProvider#summarize(AiRequest)}.
 *
 * <p>Use {@link #builder()} to construct; only {@code query} is mandatory.
 */
public final class AiRequest {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    public static final int DEFAULT_MAX_TOKENS = 512;

    private final String query;
    private final List<AiContextItem> context;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final Duration timeout;
    private final String systemPrompt;

    private AiRequest(final Builder b) {
        this.query = Objects.requireNonNull(b.query, "query must not be null");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        this.context = b.context == null ? List.of() : List.copyOf(b.context);
        this.model = b.model;
        this.maxTokens = b.maxTokens > 0 ? b.maxTokens : DEFAULT_MAX_TOKENS;
        this.temperature = b.temperature;
        this.timeout = b.timeout == null ? DEFAULT_TIMEOUT : b.timeout;
        this.systemPrompt = b.systemPrompt;
    }

    public String query() { return query; }
    public List<AiContextItem> context() { return context; }
    public String model() { return model; }
    public int maxTokens() { return maxTokens; }
    public double temperature() { return temperature; }
    public Duration timeout() { return timeout; }
    public String systemPrompt() { return systemPrompt; }

    public static Builder builder() { return new Builder(); }

    /** Builder for {@link AiRequest}. */
    public static final class Builder {
        private String query;
        private List<AiContextItem> context;
        private String model;
        private int maxTokens;
        private double temperature = 0.2;
        private Duration timeout;
        private String systemPrompt;

        public Builder query(final String v) { this.query = v; return this; }
        public Builder context(final List<AiContextItem> v) { this.context = v; return this; }
        public Builder model(final String v) { this.model = v; return this; }
        public Builder maxTokens(final int v) { this.maxTokens = v; return this; }
        public Builder temperature(final double v) { this.temperature = v; return this; }
        public Builder timeout(final Duration v) { this.timeout = v; return this; }
        public Builder systemPrompt(final String v) { this.systemPrompt = v; return this; }

        public AiRequest build() { return new AiRequest(this); }
    }
}
