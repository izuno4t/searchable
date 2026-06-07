package io.searchable.ai.ollama;

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

class OllamaProviderTest {

    private FakeHttpServer server;
    private OllamaProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = new FakeHttpServer();
        provider = new OllamaProvider(server.baseUrl(), "llama3.2", HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void name_returnsOllama() {
        assertThat(provider.name()).isEqualTo("ollama");
    }

    @Test
    void summarize_sendsGenerateRequestWithoutAuthHeader() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok("""
            {
              "model": "llama3.2",
              "response": "Local answer [doc-l]",
              "done": true,
              "prompt_eval_count": 12,
              "eval_count": 8
            }
            """));

        final AiResponse response = provider.summarize(AiRequest.builder()
            .query("local?")
            .context(List.of(new AiContextItem("doc-l", "Local", "txt", Map.of())))
            .build());

        assertThat(response.text()).isEqualTo("Local answer [doc-l]");
        assertThat(response.model()).isEqualTo("llama3.2");
        assertThat(response.citations()).containsExactly("doc-l");
        assertThat(response.usage()).containsKeys("prompt_eval_count", "eval_count");

        final FakeHttpServer.Recorded req = server.lastRecorded();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.path()).isEqualTo("/api/generate");
        assertThat(req.headers()).doesNotContainKey("authorization");

        final JsonNode payload = new ObjectMapper().readTree(req.body());
        assertThat(payload.path("model").asText()).isEqualTo("llama3.2");
        assertThat(payload.path("stream").asBoolean()).isFalse();
        assertThat(payload.path("options").path("num_predict").asInt())
            .isEqualTo(AiRequest.DEFAULT_MAX_TOKENS);
        assertThat(payload.path("prompt").asText()).contains("Question: local?");
    }

    @Test
    void summarize_usesRequestModelOverDefault() throws Exception {
        server.setNext(FakeHttpServer.CannedResponse.ok(
            "{\"model\":\"qwen2:7b\",\"response\":\"ok\"}"));

        provider.summarize(AiRequest.builder().query("q").model("qwen2:7b").build());

        final JsonNode payload = new ObjectMapper().readTree(server.lastRecorded().body());
        assertThat(payload.path("model").asText()).isEqualTo("qwen2:7b");
    }

    @Test
    void summarize_5xx_mappedToUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.error(502, "bad gateway"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void summarize_emptyResponseField_treatedAsUpstream() {
        server.setNext(FakeHttpServer.CannedResponse.ok("{\"model\":\"l\"}"));
        assertThatThrownBy(() -> provider.summarize(AiRequest.builder().query("q").build()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }
}
