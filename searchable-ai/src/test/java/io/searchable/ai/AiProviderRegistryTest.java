package io.searchable.ai;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderRegistryTest {

    @Test
    void emptyRegistry_getReturnsEmpty() {
        try (AiProviderRegistry r = new AiProviderRegistry(List.of())) {
            assertThat(r.get("openai")).isEmpty();
            assertThat(r.names()).isEmpty();
            assertThat(r.size()).isZero();
        }
    }

    @Test
    void registry_indexesByName() {
        final AiProvider a = stub("alpha");
        final AiProvider b = stub("beta");
        try (AiProviderRegistry r = new AiProviderRegistry(List.of(a, b))) {
            assertThat(r.get("alpha")).contains(a);
            assertThat(r.get("beta")).contains(b);
            assertThat(r.get("missing")).isEmpty();
            assertThat(r.names()).containsExactly("alpha", "beta");
        }
    }

    @Test
    void duplicateName_keepsFirstAndWarns() {
        final AiProvider first = stub("dupe");
        final AiProvider second = stub("dupe");
        try (AiProviderRegistry r = new AiProviderRegistry(List.of(first, second))) {
            assertThat(r.get("dupe")).contains(first);
        }
    }

    @Test
    void blankName_rejected() {
        final AiProvider blank = stub("");
        assertThatThrownBy(() -> new AiProviderRegistry(List.of(blank)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void close_invokesProviderClose() {
        final CloseRecordingProvider provider = new CloseRecordingProvider("rec");
        final AiProviderRegistry r = new AiProviderRegistry(List.of(provider));
        r.close();
        assertThat(provider.closed).isTrue();
        assertThat(r.get("rec")).isEmpty();
    }

    @Test
    void discover_returnsServiceLoaderEntries() {
        final AiProviderRegistry r = AiProviderRegistry.discover();
        assertThat(r.names()).contains("openai", "anthropic", "ollama");
        r.close();
    }

    private static AiProvider stub(final String name) {
        return new AiProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public AiResponse summarize(final AiRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class CloseRecordingProvider implements AiProvider {
        private final String name;
        boolean closed;

        CloseRecordingProvider(final String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public AiResponse summarize(final AiRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
