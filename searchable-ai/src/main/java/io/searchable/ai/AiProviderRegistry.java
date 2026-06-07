package io.searchable.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lookup table for discovered {@link AiProvider} implementations, keyed by
 * {@link AiProvider#name()}.
 *
 * <p>The registry takes ownership of the supplied providers and closes them
 * when {@link #close()} is invoked. Subsequent calls to {@link #get(String)}
 * after {@link #close()} return {@link Optional#empty()}.
 *
 * <p>Use {@link #discover()} to build a registry from all providers exposed
 * via {@link java.util.ServiceLoader}, or pass an explicit collection from
 * test code.
 */
public final class AiProviderRegistry implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final Map<String, AiProvider> byName;
    private boolean closed;

    public AiProviderRegistry(final Collection<AiProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        final Map<String, AiProvider> map = new LinkedHashMap<>();
        for (final AiProvider provider : providers) {
            final String name = Objects.requireNonNull(provider.name(),
                "provider.name() must not return null");
            if (name.isBlank()) {
                throw new IllegalArgumentException(
                    "provider name must not be blank: " + provider);
            }
            final AiProvider previous = map.putIfAbsent(name, provider);
            if (previous != null) {
                LOG.warn("Duplicate AiProvider name '{}' — keeping {}, ignoring {}",
                    name, previous.getClass().getName(), provider.getClass().getName());
            }
        }
        this.byName = Collections.unmodifiableMap(map);
    }

    /** Build a registry from all {@link AiProvider}s on the classpath. */
    public static AiProviderRegistry discover() {
        return new AiProviderRegistry(AiProvider.discover());
    }

    /** Look up a provider by its {@link AiProvider#name() name}. */
    public Optional<AiProvider> get(final String name) {
        if (closed || name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(name));
    }

    /** Identifiers of all registered providers, in registration order. */
    public Set<String> names() {
        return byName.keySet();
    }

    /** Number of registered providers. */
    public int size() {
        return byName.size();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (final AiProvider provider : byName.values()) {
            try {
                provider.close();
            } catch (final Exception e) {
                LOG.warn("Failed to close AiProvider '{}': {}",
                    provider.name(), e.getMessage(), e);
            }
        }
    }
}
