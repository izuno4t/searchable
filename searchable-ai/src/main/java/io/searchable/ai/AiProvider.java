package io.searchable.ai;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Service Provider Interface for external AI / LLM clients.
 *
 * <p>Implementations adapt a specific backend (OpenAI, Anthropic, Ollama, ...)
 * to the Searchable post-search processing pipeline. They are discovered
 * via Java's {@link ServiceLoader} and selected at runtime by
 * {@link #name()}.
 *
 * <p>Providers should be stateless from the caller's perspective; any
 * connection or HTTP client they own must be released via {@link #close()}.
 */
public interface AiProvider extends AutoCloseable {

    /**
     * Short identifier used for provider selection
     * (e.g. {@code "openai"}, {@code "anthropic"}, {@code "ollama"}).
     */
    String name();

    /**
     * Generate a summary or synthesized answer for the supplied request.
     *
     * @throws AiException when the upstream call fails (network, quota,
     *                     timeout, etc.)
     */
    AiResponse summarize(AiRequest request) throws AiException;

    /**
     * List all providers discoverable on the application classpath.
     * Order matches {@link ServiceLoader} discovery order.
     */
    static List<AiProvider> discover() {
        final List<AiProvider> all = new java.util.ArrayList<>();
        ServiceLoader.load(AiProvider.class).forEach(all::add);
        return List.copyOf(all);
    }

    @Override
    default void close() { }
}
