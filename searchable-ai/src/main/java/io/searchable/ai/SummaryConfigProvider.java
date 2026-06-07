package io.searchable.ai;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable holder for the current {@link SummaryConfig}.
 *
 * <p>Allows operators to swap the active configuration at runtime (e.g. via
 * the admin UI) without restarting the application. {@link SummaryService}
 * reads the {@link #current()} value on every
 * {@link SummaryService#summarize(String, java.util.List)} invocation, so
 * updates take effect immediately for subsequent calls.
 *
 * <p>Thread-safe: backed by an {@link AtomicReference}.
 */
public final class SummaryConfigProvider {

    private final AtomicReference<SummaryConfig> ref;

    public SummaryConfigProvider(final SummaryConfig initial) {
        this.ref = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    /** Snapshot of the currently active configuration. */
    public SummaryConfig current() {
        return ref.get();
    }

    /** Replace the active configuration. */
    public void update(final SummaryConfig next) {
        ref.set(Objects.requireNonNull(next, "next"));
    }
}
