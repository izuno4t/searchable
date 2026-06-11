package io.searchable.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage supplement for {@link AiProviderRegistry}.
 *
 * <p>Covers the conditional paths {@link AiProviderRegistryTest} does not
 * exercise: {@code get(null)} short-circuit, idempotent {@code close()},
 * and the {@code catch} that swallows an exception thrown from a provider's
 * {@code close()} so other providers still get shut down.
 */
class AiProviderRegistryBranchTest {

    @Test
    void getReturnsEmptyForNullName() {
        try (AiProviderRegistry r = new AiProviderRegistry(List.of(stub("alpha")))) {
            assertThat(r.get(null)).isEmpty();
        }
    }

    @Test
    void closeIsIdempotent() {
        final RecordingProvider p = new RecordingProvider("x");
        final AiProviderRegistry r = new AiProviderRegistry(List.of(p));
        r.close();
        r.close(); // second call must short-circuit at the `if (closed) return;`
        assertThat(p.closeCount).isEqualTo(1);
    }

    @Test
    void closeContinuesAfterProviderThrows() {
        final ThrowingProvider bad = new ThrowingProvider("bad");
        final RecordingProvider good = new RecordingProvider("good");
        final AiProviderRegistry r = new AiProviderRegistry(List.of(bad, good));

        // Must not propagate the bad provider's exception; the good one
        // still gets its close() invoked.
        r.close();

        assertThat(good.closeCount).isEqualTo(1);
    }

    private static AiProvider stub(final String name) {
        return new AiProvider() {
            @Override public String name() { return name; }
            @Override public AiResponse summarize(final AiRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class RecordingProvider implements AiProvider {
        private final String name;
        int closeCount;
        RecordingProvider(final String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public AiResponse summarize(final AiRequest request) {
            throw new UnsupportedOperationException();
        }
        @Override public void close() { closeCount++; }
    }

    private static final class ThrowingProvider implements AiProvider {
        private final String name;
        ThrowingProvider(final String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public AiResponse summarize(final AiRequest request) {
            throw new UnsupportedOperationException();
        }
        @Override public void close() {
            throw new RuntimeException("synthetic close failure");
        }
    }
}
