package io.searchable.ai.ollama;

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
 * Supplemental branch coverage for {@link OllamaProvider}. Targets malformed
 * base URL, response edge cases, usage field extraction, citation extraction
 * branches, env/system-property resolvers, and {@code toString}.
 *
 * <p>Note: unlike OpenAI / Anthropic, Ollama has no API key flow so there
 * is no AUTH branch to cover here.
 */
class OllamaProviderBranchTest {

    private FakeHttpServer server;
    private OllamaProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = new FakeHttpServer();
        provider = new OllamaProvider(server.baseUrl(), "test-model", HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    @Test
    void malformedBaseUrlIsMappedToRequest() {
        final OllamaProvider bad = new OllamaProvider(
            "::not a valid uri::", null, HttpClient.newHttpClient());
        assertThatThrownBy(() -> bad.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.REQUEST);
    }

    @Test
    void explicitSystemPromptForwarded() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok(
            "{\"model\":\"x\",\"response\":\"ok\"}"));

        provider.summarize(AiRequest.builder()
            .query("q")
            .systemPrompt("CUSTOM_SYS")
            .build());

        final var payload = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(server.lastRecorded().body());
        assertThat(payload.path("system").asText()).isEqualTo("CUSTOM_SYS");
        assertThat(payload.path("stream").asBoolean()).isFalse();
    }

    @Test
    void missingResponseFieldThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("{\"model\":\"x\"}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void emptyResponseStringThrowsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok(
            "{\"model\":\"x\",\"response\":\"\"}"));
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
    void numericUsageFieldsCopied() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {"model":"x","response":"ok",
             "prompt_eval_count":12,"eval_count":34,"total_duration":56,
             "load_duration":78,"prompt_eval_duration":90,"eval_duration":11}
            """));

        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.usage())
            .containsEntry("prompt_eval_count", 12)
            .containsEntry("eval_count", 34)
            .containsEntry("total_duration", 56)
            .containsEntry("load_duration", 78)
            .containsEntry("prompt_eval_duration", 90)
            .containsEntry("eval_duration", 11);
    }

    @Test
    void nonNumericUsageFieldStillCopiedAsText() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {"model":"x","response":"ok","prompt_eval_count":"unknown"}
            """));
        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.usage().get("prompt_eval_count")).isEqualTo("unknown");
    }

    @Test
    void absentUsageFieldsAreSkipped() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok(
            "{\"model\":\"x\",\"response\":\"ok\"}"));
        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.usage()).isEmpty();
    }

    @Test
    void modelFallsBackToConfiguredDefaultModelWhenAbsentFromResponse() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("{\"response\":\"ok\"}"));
        final AiResponse r = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(r.model()).isEqualTo("test-model");
    }

    @Test
    void extractCitationsHandlesNoMatch() {
        final List<String> hits = OllamaProvider.extractCitations(
            "no marker here",
            AiRequest.builder()
                .query("q")
                .context(List.of(new AiContextItem("d", "T", "x", Map.of())))
                .build());
        assertThat(hits).isEmpty();
    }

    @Test
    void resolveBaseUrlHonoursSystemProperty() throws Exception {
        runWith("searchable.ai.ollama.base-url", "http://override.invalid:1234", () -> {
            final OllamaProvider p = new OllamaProvider();
            assertThat(p.toString()).contains("override.invalid");
        });
    }

    @Test
    void resolveDefaultModelHonoursSystemProperty() throws Exception {
        runWith("searchable.ai.ollama.default-model", "qwen3", () -> {
            final OllamaProvider p = new OllamaProvider();
            assertThat(p.toString()).contains("defaultModel=qwen3");
        });
    }

    @Test
    void toStringExposesBaseUrlAndModel() {
        assertThat(provider.toString())
            .contains("baseUrl=")
            .contains("defaultModel=test-model");
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
