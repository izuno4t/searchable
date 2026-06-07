package io.searchable.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderTest {

    @Test
    void requestBuilderAppliesDefaultsAndRequiresQuery() {
        final AiRequest req = AiRequest.builder().query("質問").build();

        assertThat(req.query()).isEqualTo("質問");
        assertThat(req.context()).isEmpty();
        assertThat(req.maxTokens()).isEqualTo(AiRequest.DEFAULT_MAX_TOKENS);
        assertThat(req.timeout()).isEqualTo(AiRequest.DEFAULT_TIMEOUT);
    }

    @Test
    void requestBuilderRejectsBlankQuery() {
        assertThatThrownBy(() -> AiRequest.builder().query(" ").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextItemRequiresIdentifiers() {
        assertThatThrownBy(() -> new AiContextItem("", "t", "c", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exceptionExposesKind() {
        final AiException e = new AiException(AiException.Kind.TIMEOUT, "timed out");
        assertThat(e.kind()).isEqualTo(AiException.Kind.TIMEOUT);
        assertThat(e.getMessage()).contains("timed out");
    }

    @Test
    void serviceLoaderDiscoversBundledProviders() {
        // searchable-ai now ships OpenAI / Anthropic / Ollama implementations
        // (TASK-001/002/003); discover() lists them via ServiceLoader.
        assertThat(AiProvider.discover())
            .extracting(AiProvider::name)
            .contains("openai", "anthropic", "ollama");
    }

    @Test
    void providersCanBeImplementedInline() throws AiException {
        final AiProvider provider = new EchoProvider();
        final AiRequest req = AiRequest.builder()
            .query("Hello")
            .context(List.of(new AiContextItem("doc-1", "Doc", "body", Map.of())))
            .timeout(Duration.ofSeconds(5))
            .build();

        final AiResponse resp = provider.summarize(req);
        assertThat(resp.text()).contains("Hello");
        assertThat(resp.citations()).containsExactly("doc-1");
    }

    /** Reference in-memory provider used to validate the SPI surface. */
    private static final class EchoProvider implements AiProvider {
        @Override public String name() { return "echo"; }
        @Override public AiResponse summarize(final AiRequest request) {
            final List<String> ids = request.context().stream()
                .map(AiContextItem::sourceId).toList();
            return new AiResponse(
                "ECHO: " + request.query(), "echo-1",
                ids, Map.of("input-tokens", request.query().length()));
        }
    }
}
