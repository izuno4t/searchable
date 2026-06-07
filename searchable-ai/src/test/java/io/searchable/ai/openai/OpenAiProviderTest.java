package io.searchable.ai.openai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.searchable.ai.AiContextItem;
import io.searchable.ai.AiException;
import io.searchable.ai.AiRequest;
import io.searchable.ai.AiResponse;
import io.searchable.ai.testfixture.FakeHttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiProviderTest {

    private FakeHttpServer server;
    private OpenAiProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = new FakeHttpServer();
        provider = new OpenAiProvider(server.baseUrl(), "test-key", HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void name_returnsOpenai() {
        assertThat(provider.name()).isEqualTo("openai");
    }

    @Test
    void summarize_sendsAuthorizedJsonRequestAndParsesResponse() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {
              "id": "chatcmpl-1",
              "model": "gpt-4o-mini",
              "choices": [
                {"message": {"role": "assistant", "content": "Hello [doc-1]"}}
              ],
              "usage": {"prompt_tokens": 10, "completion_tokens": 4, "total_tokens": 14}
            }
            """));

        final AiResponse response = provider.summarize(AiRequest.builder()
            .query("greeting?")
            .context(List.of(new AiContextItem("doc-1", "Greeting", "Hi", Map.of())))
            .build());

        assertThat(response.text()).isEqualTo("Hello [doc-1]");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.citations()).containsExactly("doc-1");
        assertThat(response.usage()).containsKeys("prompt_tokens", "completion_tokens", "total_tokens");

        final FakeHttpServer.Recorded req = server.lastRecorded();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.path()).isEqualTo("/chat/completions");
        assertThat(req.headers().get("authorization")).containsExactly("Bearer test-key");

        final JsonNode payload = new ObjectMapper().readTree(req.body());
        assertThat(payload.path("model").asText()).isEqualTo(OpenAiProvider.DEFAULT_MODEL);
        assertThat(payload.path("max_tokens").asInt()).isEqualTo(AiRequest.DEFAULT_MAX_TOKENS);
        assertThat(payload.path("messages").isArray()).isTrue();
        assertThat(payload.path("messages").get(1).path("content").asText())
            .contains("[doc-1] Greeting", "Question: greeting?");
    }

    @Test
    void summarize_passesExplicitModelOverride() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {"model":"gpt-4o","choices":[{"message":{"content":"ok"}}],"usage":{}}
            """));

        provider.summarize(AiRequest.builder()
            .query("q")
            .model("gpt-4o")
            .build());

        final JsonNode payload = new ObjectMapper().readTree(server.lastRecorded().body());
        assertThat(payload.path("model").asText()).isEqualTo("gpt-4o");
    }

    @Test
    void summarize_withoutApiKey_throwsAuth() {
        final OpenAiProvider noKey = new OpenAiProvider(server.baseUrl(), null,
            HttpClient.newHttpClient());
        assertThatThrownBy(() -> noKey.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void summarize_authStatus_mappedToAuthException() {
        server.setNext(FakeHttpServer.CannedResponse.error(401, "{\"error\":\"unauthorized\"}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void summarize_5xx_mappedToUpstreamException() {
        server.setNext(FakeHttpServer.CannedResponse.error(503, "{\"error\":\"down\"}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void summarize_timeoutShorterThanServer_throwsTimeout() throws Exception {
        // The fake server only ever responds when sendResponseHeaders is called, but
        // our handler echoes immediately. To exercise timeout we use an unreachable
        // address (TEST-NET-1) and a very short timeout.
        try (final OpenAiProvider unreachable = new OpenAiProvider(
            "http://192.0.2.1:9", "k", HttpClient.newHttpClient())) {
            assertThatThrownBy(() -> unreachable.summarize(AiRequest.builder()
                .query("q")
                .timeout(Duration.ofMillis(100))
                .build()))
                .isInstanceOf(AiException.class)
                .extracting("kind")
                .satisfies(k -> assertThat(k)
                    .isIn(AiException.Kind.TIMEOUT, AiException.Kind.UPSTREAM));
        }
    }

    @Test
    void buildUserContent_includesAllContextItems() {
        final AiRequest r = AiRequest.builder()
            .query("q?")
            .context(List.of(
                new AiContextItem("a", "Alpha", "AA", Map.of()),
                new AiContextItem("b", "Beta", "BB", Map.of())))
            .build();
        final String body = OpenAiProvider.buildUserContent(r);
        assertThat(body).contains("[a] Alpha", "AA", "[b] Beta", "BB", "Question: q?");
    }
}
