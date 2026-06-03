package io.searchable.core.infrastructure.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Installs a {@code SIGHUP} handler that runs the supplied
 * {@link Runnable} on each delivery.
 *
 * <p>SIGHUP is the signal the CLI sends after an ingest commit so apps
 * holding the shared data directory open refresh their Lucene readers.
 *
 * <p>On platforms that do not support {@code SIGHUP} (notably Windows)
 * the listener fails silently: {@link #isInstalled()} returns
 * {@code false} and the supplied handler is never invoked. A
 * {@code WARN} log is emitted at install time so the limitation is
 * discoverable from runtime logs.
 */
public final class SighupListener {

    private static final Logger log = LoggerFactory.getLogger(SighupListener.class);
    private static final String SIGHUP_NAME = "HUP";

    private final boolean installed;
    private final sun.misc.SignalHandler previous;

    private SighupListener(final boolean installed, final sun.misc.SignalHandler previous) {
        this.installed = installed;
        this.previous = previous;
    }

    public static SighupListener install(final Runnable handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        try {
            final sun.misc.Signal sig = new sun.misc.Signal(SIGHUP_NAME);
            final sun.misc.SignalHandler prior = sun.misc.Signal.handle(sig, s -> {
                try {
                    handler.run();
                } catch (RuntimeException e) {
                    log.warn("SIGHUP handler threw", e);
                }
            });
            log.info("installed SIGHUP handler");
            return new SighupListener(true, prior);
        } catch (IllegalArgumentException e) {
            log.warn("SIGHUP is not supported on this platform; "
                + "index refresh-on-signal disabled ({})", e.getMessage());
            return new SighupListener(false, null);
        } catch (Throwable t) {
            log.warn("Failed to install SIGHUP handler; "
                + "index refresh-on-signal disabled", t);
            return new SighupListener(false, null);
        }
    }

    public boolean isInstalled() {
        return installed;
    }

    /** Restore the previously installed handler (best-effort). */
    public void uninstall() {
        if (!installed) {
            return;
        }
        try {
            sun.misc.Signal.handle(new sun.misc.Signal(SIGHUP_NAME),
                previous == null ? sun.misc.SignalHandler.SIG_DFL : previous);
        } catch (Throwable t) {
            log.debug("Failed to uninstall SIGHUP handler", t);
        }
    }
}
