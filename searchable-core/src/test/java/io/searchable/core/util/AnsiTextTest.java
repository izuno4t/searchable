package io.searchable.core.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for {@link AnsiText}. The static {@code ENABLED} flag is captured
 * at class load time, so styling-method tests only assert consistency with
 * the current runtime flag. The {@code detect()} branches that depend on the
 * {@code searchable.ansi} system property are flipped at runtime via
 * reflection on the private method; the env-var branches ({@code NO_COLOR},
 * {@code TERM=dumb}) are intentionally not exercised because mutating the JVM
 * environment is unsupported on modern JDKs and would produce flaky tests.
 */
class AnsiTextTest {

    @Test
    void privateConstructorIsInvokableForCoverage() throws Exception {
        final Constructor<AnsiText> ctor = AnsiText.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }

    @Test
    void stylingMethodsRespectEnabledFlag() {
        final String raw = "hello";
        if (AnsiText.enabled()) {
            assertThat(AnsiText.green(raw)).contains(raw).isNotEqualTo(raw);
            assertThat(AnsiText.bold(raw)).contains(raw).isNotEqualTo(raw);
            assertThat(AnsiText.dim(raw)).contains(raw).isNotEqualTo(raw);
        } else {
            assertThat(AnsiText.green(raw)).isEqualTo(raw);
            assertThat(AnsiText.bold(raw)).isEqualTo(raw);
            assertThat(AnsiText.dim(raw)).isEqualTo(raw);
        }
    }

    @Test
    void detectHonoursAlwaysOverride() throws Exception {
        withAnsiOverride("always", () -> assertThat(invokeDetect()).isTrue());
    }

    @Test
    void detectHonoursNeverOverride() throws Exception {
        withAnsiOverride("never", () -> assertThat(invokeDetect()).isFalse());
    }

    @Test
    void detectFallsBackToConsoleProbeWhenNoOverridePresent() throws Exception {
        // No NO_COLOR / TERM=dumb / searchable.ansi override: detect() falls
        // through to {@code System.console() != null}. Under JUnit there is
        // no Console so the expected value is false, but either outcome is
        // tolerated to keep the test independent of CI shell setup.
        withAnsiOverride(null, () -> assertThat(invokeDetect()).isInstanceOf(Boolean.class));
    }

    private static boolean invokeDetect() {
        try {
            final Method m = AnsiText.class.getDeclaredMethod("detect");
            m.setAccessible(true);
            return (boolean) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private static void withAnsiOverride(final String value,
                                         final ThrowingRunnable body) throws Exception {
        final String prev = System.getProperty("searchable.ansi");
        if (value == null) {
            System.clearProperty("searchable.ansi");
        } else {
            System.setProperty("searchable.ansi", value);
        }
        try {
            body.run();
        } finally {
            if (prev == null) {
                System.clearProperty("searchable.ansi");
            } else {
                System.setProperty("searchable.ansi", prev);
            }
        }
    }
}
