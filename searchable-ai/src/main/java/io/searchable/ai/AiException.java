package io.searchable.ai;

/**
 * Raised by {@link AiProvider} implementations when an upstream call fails.
 *
 * <p>Carries a coarse {@link Kind} so callers can decide whether to fall
 * back to a non-AI result (e.g. {@link Kind#TIMEOUT}) or surface the error.
 */
public class AiException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        /** Upstream request timed out before completing. */
        TIMEOUT,
        /** Authentication / authorization failure (missing or invalid API key). */
        AUTH,
        /** Provider rejected the request as malformed or over quota. */
        REQUEST,
        /** Transient upstream failure that may succeed on retry. */
        UPSTREAM,
        /** Unrecognized or unexpected failure. */
        UNKNOWN
    }

    private final Kind kind;

    public AiException(final Kind kind, final String message) {
        super(message);
        this.kind = kind;
    }

    public AiException(final Kind kind, final String message, final Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
