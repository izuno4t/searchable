package io.searchable.core.util;

/**
 * Adds ANSI color escapes when stdout/stderr is attached to a real
 * terminal, otherwise returns the raw text unchanged so log files and
 * pipes stay clean. Honors the {@code NO_COLOR} convention
 * (https://no-color.org/) and the {@code searchable.ansi=always|never}
 * system property as an explicit override.
 */
public final class AnsiText {

    private static final char ESC = 0x1B;
    private static final String RESET = ESC + "[0m";
    private static final boolean ENABLED = detect();

    private AnsiText() {
    }

    private static boolean detect() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        if ("dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return false;
        }
        final String forced = System.getProperty("searchable.ansi");
        if ("always".equalsIgnoreCase(forced)) {
            return true;
        }
        if ("never".equalsIgnoreCase(forced)) {
            return false;
        }
        return System.console() != null;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static String green(final String s) {
        return ENABLED ? ESC + "[32m" + s + RESET : s;
    }

    public static String bold(final String s) {
        return ENABLED ? ESC + "[1m" + s + RESET : s;
    }

    public static String dim(final String s) {
        return ENABLED ? ESC + "[2m" + s + RESET : s;
    }
}
