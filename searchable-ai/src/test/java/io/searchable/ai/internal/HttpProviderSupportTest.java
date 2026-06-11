package io.searchable.ai.internal;

import io.searchable.ai.AiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct branch coverage for {@link HttpProviderSupport}. All branches of
 * status-code mapping, IO-failure wrapping, response truncation, and
 * connect-timeout normalisation are pinned down here so callers
 * (OpenAI / Anthropic / Ollama providers) only need to exercise the
 * provider-specific request/response shaping in their own tests.
 */
class HttpProviderSupportTest {

    @Test
    void newClientUsesDefaultWhenNullTimeout() {
        assertThat(HttpProviderSupport.newClient(null).connectTimeout())
            .contains(Duration.ofSeconds(15));
    }

    @Test
    void newClientCapsAt30Seconds() {
        assertThat(HttpProviderSupport.newClient(Duration.ofMinutes(5)).connectTimeout())
            .contains(Duration.ofSeconds(30));
    }

    @Test
    void newClientPassesThroughBelowCap() {
        assertThat(HttpProviderSupport.newClient(Duration.ofSeconds(5)).connectTimeout())
            .contains(Duration.ofSeconds(5));
    }

    @Test
    void mapStatusAuth() {
        assertThat(HttpProviderSupport.mapStatus(401)).isEqualTo(AiException.Kind.AUTH);
        assertThat(HttpProviderSupport.mapStatus(403)).isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void mapStatusRequest() {
        for (int code : new int[]{400, 404, 422, 429}) {
            assertThat(HttpProviderSupport.mapStatus(code))
                .as("status %d", code)
                .isEqualTo(AiException.Kind.REQUEST);
        }
    }

    @Test
    void mapStatusUpstream() {
        assertThat(HttpProviderSupport.mapStatus(500)).isEqualTo(AiException.Kind.UPSTREAM);
        assertThat(HttpProviderSupport.mapStatus(503)).isEqualTo(AiException.Kind.UPSTREAM);
        assertThat(HttpProviderSupport.mapStatus(599)).isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void mapStatusUnknownForUnclassifiedCode() {
        assertThat(HttpProviderSupport.mapStatus(200)).isEqualTo(AiException.Kind.UNKNOWN);
        assertThat(HttpProviderSupport.mapStatus(302)).isEqualTo(AiException.Kind.UNKNOWN);
        assertThat(HttpProviderSupport.mapStatus(418)).isEqualTo(AiException.Kind.UNKNOWN);
    }

    @Test
    void mapStatusUnknownFor600AndAbove() {
        // status >= 500 true && status < 600 false: covers the upstream
        // upper-bound branch where the code is out of the 5xx range.
        assertThat(HttpProviderSupport.mapStatus(600)).isEqualTo(AiException.Kind.UNKNOWN);
        assertThat(HttpProviderSupport.mapStatus(999)).isEqualTo(AiException.Kind.UNKNOWN);
    }

    @Test
    void toExceptionTruncatesLongBody() {
        final String longBody = "x".repeat(2000);
        final AiException ex = HttpProviderSupport.toException(
            "openai", new StubResponse(500, longBody));
        assertThat(ex.getMessage())
            .contains("body=")
            .endsWith("...");
        assertThat(ex.kind()).isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void toExceptionShortBodyKeptVerbatim() {
        final AiException ex = HttpProviderSupport.toException(
            "openai", new StubResponse(429, "rate limited"));
        assertThat(ex.getMessage()).contains("rate limited");
        assertThat(ex.kind()).isEqualTo(AiException.Kind.REQUEST);
    }

    @Test
    void toExceptionTreatsNullBodyAsEmpty() {
        final AiException ex = HttpProviderSupport.toException(
            "openai", new StubResponse(401, null));
        assertThat(ex.kind()).isEqualTo(AiException.Kind.AUTH);
        assertThat(ex.getMessage()).contains("body=");
    }

    @Test
    void wrapIoFailureTimeoutCase() {
        final AiException ex = HttpProviderSupport.wrapIoFailure(
            "ollama", new HttpTimeoutException("connect timed out"));
        assertThat(ex.kind()).isEqualTo(AiException.Kind.TIMEOUT);
        assertThat(ex.getMessage()).contains("ollama I/O failure");
    }

    @Test
    void wrapIoFailureGenericCase() {
        final AiException ex = HttpProviderSupport.wrapIoFailure(
            "anthropic", new IOException("reset"));
        assertThat(ex.kind()).isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void wrapInterruptedReassertsInterrupt() {
        // Clear interrupt flag first so we can assert wrapInterrupted set it.
        Thread.interrupted();
        final AiException ex = HttpProviderSupport.wrapInterrupted(
            "openai", new InterruptedException("synthetic"));
        assertThat(ex.kind()).isEqualTo(AiException.Kind.UNKNOWN);
        assertThat(Thread.interrupted()).isTrue(); // also clears for cleanup
    }

    /** Minimal HttpResponse stub for the body/status mapping branches. */
    private static final class StubResponse implements HttpResponse<String> {
        private final int status;
        private final String body;
        StubResponse(final int status, final String body) {
            this.status = status;
            this.body = body;
        }
        @Override public int statusCode() { return status; }
        @Override public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://example.invalid/")).build();
        }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }
        @Override public String body() { return body; }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://example.invalid/"); }
        @Override public java.net.http.HttpClient.Version version() {
            return java.net.http.HttpClient.Version.HTTP_1_1;
        }
    }
}
