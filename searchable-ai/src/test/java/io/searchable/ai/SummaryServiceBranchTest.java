package io.searchable.ai;

import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplemental coverage for {@link SummaryService}: the SearchResult overload,
 * null/empty branches inside {@code toContextItem}, the
 * {@code maxContextChars} short-circuit, and the {@code isDisabled} /
 * {@code isFallback} null-tolerant getters.
 */
class SummaryServiceBranchTest {

    @Test
    void summarizeWithSearchResultOverload_returnsDisabledWhenConfigDisabled() throws AiException {
        final AiProviderRegistry registry = new AiProviderRegistry(List.of());
        final SummaryConfigProvider provider = new SummaryConfigProvider(SummaryConfig.disabled());
        final SummaryService service = new SummaryService(registry, provider);

        final SearchResult sr = SearchResult.empty(0);
        final AiResponse r = service.summarize("q", sr);

        assertThat(SummaryService.isDisabled(r)).isTrue();
        registry.close();
    }

    @Test
    void toContextItem_coercesNullContentToEmptyString() {
        final Map<String, Object> meta = new HashMap<>();
        meta.put("category", "spec");
        final SearchHit hit = new SearchHit(
            "d", "n", "T", null, 0.5, Map.of(), meta, List.of());

        final AiContextItem item = SummaryService.toContextItem(hit);
        assertThat(item.text()).isEmpty();
        assertThat(item.title()).isEqualTo("T");
        assertThat(item.metadata()).containsEntry("namespace", "n");
        assertThat(item.metadata()).containsEntry("score", 0.5);
        assertThat(item.metadata()).containsEntry("category", "spec");
    }

    @Test
    void fallbackResponse_handlesExceptionWithNullMessage() {
        final AiException ex = new AiException(AiException.Kind.UPSTREAM, null);
        final AiResponse r = SummaryService.fallbackResponse(ex);
        assertThat(r.usage().get("error.kind")).isEqualTo("UPSTREAM");
        assertThat(r.usage().get("error.message")).isEqualTo("");
        assertThat(SummaryService.isFallback(r)).isTrue();
    }

    @Test
    void isDisabledReturnsFalseForNull() {
        assertThat(SummaryService.isDisabled(null)).isFalse();
    }

    @Test
    void isFallbackReturnsFalseForNull() {
        assertThat(SummaryService.isFallback(null)).isFalse();
    }

    @Test
    void isDisabledReturnsFalseForOtherModel() {
        final AiResponse other = new AiResponse("x", "gpt-4o", List.of(), Map.of());
        assertThat(SummaryService.isDisabled(other)).isFalse();
        assertThat(SummaryService.isFallback(other)).isFalse();
    }

    @Test
    void summarize_unknownProviderName_returnsFallbackWhenEnabled() throws AiException {
        final AiProviderRegistry registry = new AiProviderRegistry(List.of());
        // Configure a provider name the registry doesn't know.
        final SummaryConfigProvider provider = new SummaryConfigProvider(
            SummaryConfig.forProvider("nonexistent"));
        final SummaryService service = new SummaryService(registry, provider);

        final AiResponse r = service.summarize("q", List.of());

        assertThat(SummaryService.isFallback(r)).isTrue();
        assertThat(r.usage().get("error.kind")).isEqualTo("REQUEST");
        registry.close();
    }

    @Test
    void summarize_maxContextCharsCutsListMidway() throws AiException {
        // We exercise limitContext indirectly via toContextItems + a stub
        // provider that echoes the size of the context it received.
        final EchoContextSizeProvider echo = new EchoContextSizeProvider();
        final AiProviderRegistry registry = new AiProviderRegistry(List.of(echo));
        final SummaryConfig limit = new SummaryConfig(
            "echo", null, Duration.ofSeconds(1), 1, 0.0,
            /* maxContextItems */ 10,
            /* maxContextChars */ 20, // small cap forces an early break
            true);
        final SummaryConfigProvider provider = new SummaryConfigProvider(limit);
        final SummaryService service = new SummaryService(registry, provider);

        final List<AiContextItem> items = List.of(
            new AiContextItem("d1", "T", "1234567890", Map.of()),    // 10 chars
            new AiContextItem("d2", "T", "1234567890", Map.of()),    // +10 = 20 — OK
            new AiContextItem("d3", "T", "1234567890", Map.of()));   // would overflow → break

        final AiResponse r = service.summarize("q", items);
        assertThat(r.text()).isEqualTo("size=2");
        registry.close();
    }

    private static final class EchoContextSizeProvider implements AiProvider {
        @Override public String name() { return "echo"; }
        @Override public AiResponse summarize(final AiRequest request) {
            return new AiResponse("size=" + request.context().size(),
                "echo", List.of(), Map.of());
        }
    }
}
