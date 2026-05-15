package com.searchable.core.application.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable holder for the active {@link GlobalConfig}.
 *
 * <p>Allows the admin UI to change global defaults at runtime without
 * restarting the application. Newly created namespaces pick up the
 * current value via {@link #current()}.
 */
public final class GlobalConfigProvider {

    private final AtomicReference<GlobalConfig> ref;

    public GlobalConfigProvider(final GlobalConfig initial) {
        this.ref = new AtomicReference<>(Objects.requireNonNull(initial));
    }

    public GlobalConfig current() {
        return ref.get();
    }

    public void update(final GlobalConfig next) {
        Objects.requireNonNull(next, "next must not be null");
        ref.set(next);
    }
}
