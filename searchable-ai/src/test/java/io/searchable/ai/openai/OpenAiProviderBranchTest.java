package io.searchable.ai.openai;

import io.searchable.ai.AiContextItem;
import io.searchable.ai.AiException;
import io.searchable.ai.AiRequest;
import io.searchable.ai.AiResponse;
import io.searchable.ai.testfixture.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Supplemental branch coverage for {@link OpenAiProvider}. Complements
 * {@link OpenAiProviderTest} (happy path / auth / 5xx / timeout) with the
 * remaining defensive branches: blank API key, malformed base URL, response
 * shape edge cases, citation extraction with no matches, env/system-property
 * resolvers, and {@code toString}.
 */
class OpenAiProviderBranchTest {

    private FakeHttpServer server;
    private OpenAiProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = new FakeHttpServer();
        provider = new OpenAiProvider(server.baseUrl(), "test-key", HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    @Test
    void blankApiKeyThrowsAuth() {
        final OpenAiProvider blank = new OpenAiProvider(server.baseUrl(), "   ",
            HttpClient.newHttpClient());
        assertThatThrownBy(() -> blank.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void malformedBaseUrlIsMappedToRequest() {
        final OpenAiProvider bad = new OpenAiProvider(
            "::not a valid uri::", "key", HttpClient.newHttpClient());
        assertThatThrownBy(() -> bad.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.REQUEST);
    }

    @Test
    void explicitSystemPromptIsForwarded() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok(
            "{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{}}"));

        provider.summarize(AiRequest.builder()
            .query("q")
            .systemPrompt("CUSTOM_SYS")
            .build());

        final var payload = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(server.lastRecorded().body());
        assertThat(payload.path("messages").get(0).path("content").asText())
            .isEqualTo("CUSTOM_SYS");
    }

    @Test
    void emptyChoicesArrayThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("{\"choices\":[]}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void missingChoicesFieldThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("{}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void malformedJsonBodyThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("not-json"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void missingUsageNodeProducesEmptyUsageMap() throws Exception {
        // No `usage` field in the response → `usageNode.isObject()` returns
        // false and the for-loop is skipped, leaving usage as an empty map.
        server.setNext(FakeHttpServer.CannedResponse.ok(
            "{\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));
        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.text()).isEqualTo("ok");
        assertThat(r.usage()).isEmpty();
    }

    @Test
    void extractCitationsSkipsDuplicateSourceIdInContext() {
        // Same sourceId appearing twice in the request context: the second
        // pass through the loop hits `hits.contains(item.sourceId())==true`
        // so it is not added a second time.
        final List<String> hits = OpenAiProvider.extractCitations(
            "see [doc-1]",
            AiRequest.builder()
                .query("q")
                .context(List.of(
                    new AiContextItem("doc-1", "D", "x", Map.of()),
                    new AiContextItem("doc-1", "D again", "y", Map.of())))
                .build());
        assertThat(hits).containsExactly("doc-1");
    }

    @Test
    void usageNodeWithMixedTypesCopied() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {
              "model":"gpt-4o-mini",
              "choices":[{"message":{"content":"ok"}}],
              "usage":{"prompt_tokens":10, "system_fingerprint":"fp_abc"}
            }
            """));

        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.usage().get("prompt_tokens")).isEqualTo(10);
        assertThat(r.usage().get("system_fingerprint")).isEqualTo("fp_abc");
    }

    @Test
    void extractCitationsReturnsEmptyWhenNoMarkerPresent() {
        final List<String> hits = OpenAiProvider.extractCitations(
            "no markers here",
            AiRequest.builder()
                .query("q")
                .context(List.of(new AiContextItem("doc-x", "X", "x", Map.of())))
                .build());
        assertThat(hits).isEmpty();
    }

    @Test
    void extractCitationsDeduplicatesRepeatedMarkers() {
        final List<String> hits = OpenAiProvider.extractCitations(
            "see [doc-1] and again [doc-1]",
            AiRequest.builder()
                .query("q")
                .context(List.of(new AiContextItem("doc-1", "D", "x", Map.of())))
                .build());
        assertThat(hits).containsExactly("doc-1");
    }

    @Test
    void blankSystemPropertiesFallThroughToDefaults() throws Exception {
        // Blank base-url/api-key system properties: the `!prop.isBlank()=false`
        // branch in both resolveBaseUrl and resolveApiKey runs.
        runWith("searchable.ai.openai.base-url", "   ", () -> {
            runWith("searchable.ai.openai.api-key", "   ", () -> {
                final OpenAiProvider p = new OpenAiProvider();
                assertThat(p.toString()).contains("baseUrl=");
            });
        });
    }

    @Test
    void resolveBaseUrlHonoursSystemProperty() throws Exception {
        runWith("searchable.ai.openai.base-url", "https://custom.example.invalid/v1", () -> {
            final OpenAiProvider p = new OpenAiProvider();
            assertThat(p.toString()).contains("custom.example.invalid");
        });
    }

    @Test
    void resolveApiKeyHonoursSystemProperty() throws Exception {
        runWith("searchable.ai.openai.api-key", "sk-test", () -> {
            final OpenAiProvider p = new OpenAiProvider();
            // toString masks the value but signals "configured"
            assertThat(p.toString()).contains("apiKeyConfigured=true");
        });
    }

    @Test
    void toStringSignalsUnconfiguredApiKey() {
        final OpenAiProvider unconfigured = new OpenAiProvider(
            server.baseUrl(), null, HttpClient.newHttpClient());
        assertThat(unconfigured.toString()).contains("apiKeyConfigured=false");
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private static void runWith(final String key, final String value,
                                final ThrowingRunnable body) throws Exception {
        final String prev = System.getProperty(key);
        System.setProperty(key, value);
        try {
            body.run();
        } finally {
            if (prev == null) System.clearProperty(key);
            else System.setProperty(key, prev);
        }
    }
}
