package io.searchable.ai.anthropic;

import java.net.http.HttpClient;
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

class AnthropicProviderTest {

    private FakeHttpServer server;
    private AnthropicProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = new FakeHttpServer();
        provider = new AnthropicProvider(server.baseUrl(), "anth-key",
            AnthropicProvider.DEFAULT_API_VERSION, HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void name_returnsAnthropic() {
        assertThat(provider.name()).isEqualTo("anthropic");
    }

    @Test
    void summarize_sendsKeyAndVersionAndParsesContentBlocks() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {
              "id": "msg_1",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [
                {"type": "text", "text": "Answer [doc-x]"}
              ],
              "usage": {"input_tokens": 12, "output_tokens": 5}
            }
            """));

        final AiResponse response = provider.summarize(AiRequest.builder()
            .query("what?")
            .context(List.of(new AiContextItem("doc-x", "X", "x text", Map.of())))
            .build());

        assertThat(response.text()).isEqualTo("Answer [doc-x]");
        assertThat(response.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(response.citations()).containsExactly("doc-x");
        assertThat(response.usage()).containsKeys("input_tokens", "output_tokens");

        final FakeHttpServer.Recorded req = server.lastRecorded();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.path()).isEqualTo("/v1/messages");
        assertThat(req.headers().get("x-api-key")).containsExactly("anth-key");
        assertThat(req.headers().get("anthropic-version"))
            .containsExactly(AnthropicProvider.DEFAULT_API_VERSION);

        final JsonNode payload = new ObjectMapper().readTree(req.body());
        assertThat(payload.path("model").asText()).isEqualTo(AnthropicProvider.DEFAULT_MODEL);
        assertThat(payload.path("system").asText()).isNotEmpty();
        assertThat(payload.path("messages").isArray()).isTrue();
        assertThat(payload.path("messages").get(0).path("role").asText()).isEqualTo("user");
    }

    @Test
    void summarize_concatenatesMultipleTextBlocks() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {
              "model": "claude-sonnet-4-6",
              "content": [
                {"type": "text", "text": "first "},
                {"type": "text", "text": "second"}
              ],
              "usage": {}
            }
            """));

        final AiResponse response = provider.summarize(AiRequest.builder().query("q").build());
        assertThat(response.text()).isEqualTo("first second");
    }

    @Test
    void summarize_withoutApiKey_throwsAuth() {
        final AnthropicProvider noKey = new AnthropicProvider(server.baseUrl(), null,
            AnthropicProvider.DEFAULT_API_VERSION, HttpClient.newHttpClient());
        assertThatThrownBy(() -> noKey.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void summarize_authStatus_mappedToAuth() {
        server.setNext(FakeHttpServer.CannedResponse.error(403, "{}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void summarize_429_mappedToRequest() {
        server.setNext(FakeHttpServer.CannedResponse.error(429, "rate limited"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.REQUEST);
    }
}
