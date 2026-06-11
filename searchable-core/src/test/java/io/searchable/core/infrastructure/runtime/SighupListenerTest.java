package io.searchable.core.infrastructure.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage for {@link SighupListener} install / uninstall lifecycle. The
 * actual signal-delivery path is not exercised here — sending SIGHUP to
 * the test JVM would tear down surefire — but the install branch and
 * the {@code uninstall} idempotency branches are reachable without
 * raising a real signal.
 */
class SighupListenerTest {

    @Test
    void installRejectsNullHandler() {
        assertThatThrownBy(() -> SighupListener.install(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void installSucceedsOnUnixThenUninstalls() {
        Assumptions.assumeFalse(
            System.getProperty("os.name", "").toLowerCase().contains("win"),
            "SIGHUP is not supported on Windows");
        final AtomicInteger counter = new AtomicInteger();
        final SighupListener listener = SighupListener.install(counter::incrementAndGet);

        assertThat(listener.isInstalled()).isTrue();
        // Idempotent uninstall: explicit + a second no-op call should both be safe.
        listener.uninstall();
        listener.uninstall();
    }

    @Test
    void uninstallIsNoOpWhenNotInstalled() {
        // Synthetic instance via reflection of the private constructor: we
        // assert the early-return inside uninstall() when installed=false.
        try {
            final var ctor = SighupListener.class.getDeclaredConstructor(
                boolean.class, sun.misc.SignalHandler.class);
            ctor.setAccessible(true);
            final SighupListener notInstalled = ctor.newInstance(false, null);
            notInstalled.uninstall();
            assertThat(notInstalled.isInstalled()).isFalse();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
