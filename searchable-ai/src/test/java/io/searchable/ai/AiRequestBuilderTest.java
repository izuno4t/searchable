package io.searchable.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestBuilderTest {

    @Test
    void builderSetsEveryField() {
        final AiContextItem item = new AiContextItem("doc-1", "Doc", "body", Map.of());
        final AiRequest req = AiRequest.builder()
            .query("Why?")
            .context(List.of(item))
            .model("test-model")
            .maxTokens(2048)
            .temperature(0.9)
            .timeout(Duration.ofSeconds(30))
            .systemPrompt("be brief")
            .build();

        assertThat(req.query()).isEqualTo("Why?");
        assertThat(req.context()).containsExactly(item);
        assertThat(req.model()).isEqualTo("test-model");
        assertThat(req.maxTokens()).isEqualTo(2048);
        assertThat(req.temperature()).isEqualTo(0.9);
        assertThat(req.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(req.systemPrompt()).isEqualTo("be brief");
    }

    @Test
    void nullQueryRejected() {
        assertThatThrownBy(() -> AiRequest.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("query");
    }

    @Test
    void nonPositiveMaxTokensFallsBackToDefault() {
        final AiRequest zero = AiRequest.builder().query("q").maxTokens(0).build();
        final AiRequest negative = AiRequest.builder().query("q").maxTokens(-99).build();

        assertThat(zero.maxTokens()).isEqualTo(AiRequest.DEFAULT_MAX_TOKENS);
        assertThat(negative.maxTokens()).isEqualTo(AiRequest.DEFAULT_MAX_TOKENS);
    }

    @Test
    void contextListIsDefensivelyCopied() {
        final java.util.List<AiContextItem> mutable = new java.util.ArrayList<>();
        mutable.add(new AiContextItem("a", "A", "a-text", Map.of()));
        final AiRequest req = AiRequest.builder().query("q").context(mutable).build();

        mutable.add(new AiContextItem("b", "B", "b-text", Map.of()));

        assertThat(req.context()).hasSize(1);
    }

    @Test
    void contextItemDefensiveCopyForMetadata() {
        final java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("k", "v");
        final AiContextItem item = new AiContextItem("id", "t", "c", meta);

        meta.put("k", "MUTATED");
        meta.put("k2", "v2");

        assertThat(item.metadata()).containsOnlyKeys("k");
        assertThat(item.metadata().get("k")).isEqualTo("v");
    }

    @Test
    void contextItemRejectsNullFields() {
        assertThatThrownBy(() -> new AiContextItem(null, "t", "c", Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiContextItem("id", null, "c", Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiContextItem("id", "t", null, Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void contextItemNullMetadataBecomesEmpty() {
        final AiContextItem item = new AiContextItem("id", "t", "c", null);
        assertThat(item.metadata()).isEmpty();
    }

    @Test
    void aiResponseRejectsNullTextOrModel() {
        assertThatThrownBy(() -> new AiResponse(null, "m", List.of(), Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiResponse("text", null, List.of(), Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aiResponseNullCollectionsBecomeEmpty() {
        final AiResponse resp = new AiResponse("hi", "m", null, null);
        assertThat(resp.citations()).isEmpty();
        assertThat(resp.usage()).isEmpty();
    }

    @Test
    void aiExceptionPreservesCauseAndKind() {
        final RuntimeException root = new RuntimeException("network down");
        final AiException ex = new AiException(AiException.Kind.UPSTREAM, "wrapped", root);

        assertThat(ex.kind()).isEqualTo(AiException.Kind.UPSTREAM);
        assertThat(ex.getCause()).isSameAs(root);
        assertThat(ex.getMessage()).isEqualTo("wrapped");
    }

    @Test
    void aiExceptionKindEnumValuesAreReachable() {
        // Calling .values() and valueOf wires up coverage for the enum.
        assertThat(AiException.Kind.values()).contains(
            AiException.Kind.TIMEOUT,
            AiException.Kind.AUTH,
            AiException.Kind.REQUEST,
            AiException.Kind.UPSTREAM,
            AiException.Kind.UNKNOWN);
        assertThat(AiException.Kind.valueOf("AUTH")).isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void aiProviderDefaultCloseIsNoOp() {
        final AiProvider provider = new AiProvider() {
            @Override public String name() { return "noop"; }
            @Override public AiResponse summarize(final AiRequest request) {
                return new AiResponse("", "m", List.of(), Map.of());
            }
        };
        provider.close();
        provider.close();
    }
}
