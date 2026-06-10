package io.searchable.core.application.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable holder for the active {@link SearchableGlobalConfig}.
 *
 * <p>Allows the admin UI to change global defaults at runtime without
 * restarting the application. Newly created namespaces pick up the
 * current value via {@link #current()}.
 */
public final class SearchableGlobalConfigProvider {

    private final AtomicReference<SearchableGlobalConfig> ref;

    public SearchableGlobalConfigProvider(final SearchableGlobalConfig initial) {
        this.ref = new AtomicReference<>(Objects.requireNonNull(initial));
    }

    public SearchableGlobalConfig current() {
        return ref.get();
    }

    public void update(final SearchableGlobalConfig next) {
        Objects.requireNonNull(next, "next must not be null");
        ref.set(next);
    }
}
