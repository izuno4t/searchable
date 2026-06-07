package io.searchable.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummaryServiceTest {

    private static SummaryConfig fallbackConfig(final String provider, final boolean fallback) {
        return new SummaryConfig(provider, "model-x", Duration.ofSeconds(5),
            128, 0.2, 5, 2000, fallback);
    }

    @Test
    void disabledConfig_returnsDisabledResponse() throws Exception {
        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of()), SummaryConfig.disabled());
        final AiResponse response = service.summarize("q", List.<AiContextItem>of());
        assertThat(SummaryService.isDisabled(response)).isTrue();
    }

    @Test
    void unknownProvider_returnsFallbackResponse() throws Exception {
        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of()), fallbackConfig("nope", true));
        final AiResponse response = service.summarize("q", List.<AiContextItem>of());
        assertThat(SummaryService.isFallback(response)).isTrue();
        assertThat(response.usage()).containsKey("error.kind");
    }

    @Test
    void summarize_dispatchesToProvider() throws Exception {
        final RecordingProvider provider = new RecordingProvider("rec",
            new AiResponse("ok", "model-x", List.of(), Map.of()));
        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of(provider)), fallbackConfig("rec", true));

        final AiResponse response = service.summarize("hello",
            List.of(new AiContextItem("d-1", "T", "txt", Map.of())));

        assertThat(response.text()).isEqualTo("ok");
        assertThat(provider.lastRequest.query()).isEqualTo("hello");
        assertThat(provider.lastRequest.context()).extracting(AiContextItem::sourceId)
            .containsExactly("d-1");
        assertThat(provider.lastRequest.model()).isEqualTo("model-x");
    }

    @Test
    void timeoutFromProvider_convertedToFallbackWhenEnabled() throws Exception {
        final RecordingProvider provider = new RecordingProvider("tp",
            new AiException(AiException.Kind.TIMEOUT, "slow"));
        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of(provider)), fallbackConfig("tp", true));

        final AiResponse response = service.summarize("q", List.<AiContextItem>of());
        assertThat(SummaryService.isFallback(response)).isTrue();
        assertThat(response.usage().get("error.kind")).isEqualTo("TIMEOUT");
    }

    @Test
    void upstreamFailure_rethrownWhenFallbackDisabled() {
        final RecordingProvider provider = new RecordingProvider("xp",
            new AiException(AiException.Kind.UPSTREAM, "down"));
        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of(provider)), fallbackConfig("xp", false));
        assertThatThrownBy(() -> service.summarize("q", List.<AiContextItem>of()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.UPSTREAM);
    }

    @Test
    void authFailure_alwaysRethrown_evenWithFallback() {
        final RecordingProvider provider = new RecordingProvider("ap",
            new AiException(AiException.Kind.AUTH, "no key"));
        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of(provider)), fallbackConfig("ap", true));
        assertThatThrownBy(() -> service.summarize("q", List.<AiContextItem>of()))
            .isInstanceOf(AiException.class)
            .extracting("kind").isEqualTo(AiException.Kind.AUTH);
    }

    @Test
    void searchResultMapping_convertsHitsToContextItems() {
        final SearchHit hit = new SearchHit("d-1", "ns-A", "Title", "Body", 1.5,
            Map.of(), Map.of("url", "https://example.com"), List.of());
        final SearchResult result = new SearchResult(List.of(hit), 1L, 1.5, Map.of(), 10L);

        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of()), SummaryConfig.disabled());
        final List<AiContextItem> items = service.toContextItems(result);

        assertThat(items).hasSize(1);
        final AiContextItem item = items.get(0);
        assertThat(item.sourceId()).isEqualTo("d-1");
        assertThat(item.title()).isEqualTo("Title");
        assertThat(item.text()).isEqualTo("Body");
        assertThat(item.metadata()).containsEntry("namespace", "ns-A")
            .containsEntry("score", 1.5)
            .containsEntry("url", "https://example.com");
    }

    @Test
    void limitContext_capsByMaxItemsAndChars() throws Exception {
        final RecordingProvider provider = new RecordingProvider("lim",
            new AiResponse("ok", "m", List.of(), Map.of()));
        final SummaryConfig limited = new SummaryConfig("lim", "m", Duration.ofSeconds(1),
            128, 0.0, 2, 50, true);
        final SummaryService service = new SummaryService(
            new AiProviderRegistry(List.of(provider)), limited);

        // Three items, the third one is too large to fit under maxContextChars.
        service.summarize("q", List.of(
            new AiContextItem("a", "A", "a".repeat(20), Map.of()),
            new AiContextItem("b", "B", "b".repeat(20), Map.of()),
            new AiContextItem("c", "C", "c".repeat(20), Map.of())
        ));

        // maxContextItems=2 cap applies before the char check.
        assertThat(provider.lastRequest.context()).extracting(AiContextItem::sourceId)
            .containsExactly("a", "b");
    }

    /** Minimal provider that records requests and returns a fixed response (or exception). */
    private static final class RecordingProvider implements AiProvider {
        private final String name;
        private final AiResponse response;
        private final AiException error;
        AiRequest lastRequest;

        RecordingProvider(final String name, final AiResponse response) {
            this.name = name;
            this.response = response;
            this.error = null;
        }

        RecordingProvider(final String name, final AiException error) {
            this.name = name;
            this.response = null;
            this.error = error;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public AiResponse summarize(final AiRequest request) throws AiException {
            this.lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
