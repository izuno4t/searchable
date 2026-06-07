package io.searchable.ai.internal;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import io.searchable.ai.AiException;

/**
 * Internal helpers shared by HTTP-backed {@link io.searchable.ai.AiProvider}
 * implementations (OpenAI / Anthropic / Ollama). Centralises HTTP client
 * construction, status-code → {@link AiException.Kind} mapping, and error
 * body truncation.
 *
 * <p>Package-internal: not part of the SPI contract; subject to change.
 */
public final class HttpProviderSupport {

    private static final int ERROR_BODY_LIMIT = 512;

    private HttpProviderSupport() { }

    /**
     * Build an {@link HttpClient} suitable for AI provider use. The
     * connect timeout is set from the supplied request timeout (capped at
     * 30 s); per-request {@code Duration timeout} on the
     * {@link java.net.http.HttpRequest.Builder} is set separately.
     */
    public static HttpClient newClient(final Duration connectTimeout) {
        final Duration cap = connectTimeout == null
            ? Duration.ofSeconds(15)
            : connectTimeout.compareTo(Duration.ofSeconds(30)) > 0
                ? Duration.ofSeconds(30) : connectTimeout;
        return HttpClient.newBuilder()
            .connectTimeout(cap)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Translate an HTTP status code into an {@link AiException} with the
     * provider's name embedded so callers can route fallback logic.
     *
     * @param providerName short provider id (e.g. {@code "openai"})
     * @param response     response whose body and status are inspected
     */
    public static AiException toException(final String providerName,
                                          final HttpResponse<String> response) {
        final int status = response.statusCode();
        final String body = truncate(response.body());
        final AiException.Kind kind = mapStatus(status);
        final String message = String.format(Locale.ROOT,
            "%s upstream call failed: status=%d body=%s", providerName, status, body);
        return new AiException(kind, message);
    }

    /** Map an HTTP status to {@link AiException.Kind}. */
    public static AiException.Kind mapStatus(final int status) {
        if (status == 401 || status == 403) {
            return AiException.Kind.AUTH;
        }
        if (status == 400 || status == 404 || status == 422 || status == 429) {
            return AiException.Kind.REQUEST;
        }
        if (status >= 500 && status < 600) {
            return AiException.Kind.UPSTREAM;
        }
        return AiException.Kind.UNKNOWN;
    }

    /**
     * Wrap a low-level I/O failure into an {@link AiException}, distinguishing
     * {@link java.net.http.HttpTimeoutException} from generic
     * {@link IOException} for callers that want to fall back without
     * surfacing the error.
     */
    public static AiException wrapIoFailure(final String providerName, final IOException cause) {
        final boolean timeout = cause instanceof java.net.http.HttpTimeoutException;
        final AiException.Kind kind = timeout
            ? AiException.Kind.TIMEOUT
            : AiException.Kind.UPSTREAM;
        return new AiException(kind, providerName + " I/O failure: " + cause.getMessage(), cause);
    }

    /**
     * Wrap an {@link InterruptedException} encountered while awaiting an HTTP
     * response. Re-asserts the interrupt status so callers can propagate.
     */
    public static AiException wrapInterrupted(final String providerName,
                                              final InterruptedException cause) {
        Thread.currentThread().interrupt();
        return new AiException(AiException.Kind.UNKNOWN,
            providerName + " call interrupted", cause);
    }

    private static String truncate(final String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= ERROR_BODY_LIMIT) {
            return body;
        }
        return body.substring(0, ERROR_BODY_LIMIT) + "...";
    }
}
