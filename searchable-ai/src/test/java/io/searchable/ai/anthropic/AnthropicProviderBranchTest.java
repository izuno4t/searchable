package io.searchable.ai.anthropic;

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
 * Supplemental branch coverage for {@link AnthropicProvider} mirroring
 * {@code OpenAiProviderBranchTest}. Targets blank API key, malformed base
 * URL, response edge cases, citation extraction, env/system-property
 * resolvers, and {@code toString}.
 */
class AnthropicProviderBranchTest {

    private FakeHttpServer server;
    private AnthropicProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = new FakeHttpServer();
        provider = new AnthropicProvider(server.baseUrl(), "test-key",
            "2023-06-01", HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    @Test
    void blankApiKeyThrowsAuth() {
        final AnthropicProvider blank = new AnthropicProvider(
            server.baseUrl(), "   ", null, HttpClient.newHttpClient());
        assertThatThrownBy(() -> blank.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void malformedBaseUrlIsMappedToRequest() {
        final AnthropicProvider bad = new AnthropicProvider(
            "::not a valid uri::", "k", null, HttpClient.newHttpClient());
        assertThatThrownBy(() -> bad.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.REQUEST);
    }

    @Test
    void explicitSystemPromptForwardedAtTopLevel() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {"model":"claude","content":[{"type":"text","text":"ok"}],"usage":{}}
            """));

        provider.summarize(AiRequest.builder()
            .query("q")
            .systemPrompt("CUSTOM_SYS")
            .build());

        final var payload = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(server.lastRecorded().body());
        assertThat(payload.path("system").asText()).isEqualTo("CUSTOM_SYS");
    }

    @Test
    void emptyContentArrayThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("{\"content\":[]}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void missingContentFieldThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("{}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void malformedJsonThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("not-json"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void multipleTextBlocksAreConcatenated() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {"model":"claude","content":[
              {"type":"text","text":"alpha "},
              {"type":"text","text":"beta"},
              {"type":"image","source":{"url":"x"}}
            ],"usage":{}}
            """));
        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.text()).isEqualTo("alpha beta");
    }

    @Test
    void usageNodeWithMixedTypesCopied() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {"model":"claude","content":[{"type":"text","text":"ok"}],
             "usage":{"input_tokens":7,"cache_state":"hot"}}
            """));
        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.usage().get("input_tokens")).isEqualTo(7);
        assertThat(r.usage().get("cache_state")).isEqualTo("hot");
    }

    @Test
    void extractCitationsEmptyWhenNoMarker() {
        final List<String> hits = AnthropicProvider.extractCitations(
            "plain text",
            AiRequest.builder()
                .query("q")
                .context(List.of(new AiContextItem("doc-x", "X", "x", Map.of())))
                .build());
        assertThat(hits).isEmpty();
    }

    @Test
    void resolveBaseUrlHonoursSystemProperty() throws Exception {
        runWith("searchable.ai.anthropic.base-url", "https://custom.example.invalid", () -> {
            final AnthropicProvider p = new AnthropicProvider();
            assertThat(p.toString()).contains("custom.example.invalid");
        });
    }

    @Test
    void resolveApiKeyHonoursSystemProperty() throws Exception {
        runWith("searchable.ai.anthropic.api-key", "sk-ant-test", () -> {
            final AnthropicProvider p = new AnthropicProvider();
            assertThat(p.toString()).contains("apiKeyConfigured=true");
        });
    }

    @Test
    void resolveApiVersionHonoursSystemProperty() throws Exception {
        runWith("searchable.ai.anthropic.api-version", "2025-01-01", () -> {
            final AnthropicProvider p = new AnthropicProvider();
            assertThat(p.toString()).contains("apiVersion=2025-01-01");
        });
    }

    @Test
    void toStringSignalsUnconfiguredApiKey() {
        final AnthropicProvider unconfigured = new AnthropicProvider(
            server.baseUrl(), null, null, HttpClient.newHttpClient());
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
