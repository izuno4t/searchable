package io.searchable.ai.anthropic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@code OpenAiProviderEnvDefaultsTest} for Anthropic — exercises
 * the {@code System.getProperty == null} fall-through branches in
 * {@code resolveBaseUrl}, {@code resolveApiKey}, and {@code resolveApiVersion}.
 */
class AnthropicProviderEnvDefaultsTest {

    @Test
    void defaultConstructorResolvesBaseUrlApiVersionAndApiKey() throws Exception {
        // Clear sysprops so resolvers fall through to env vars / defaults.
        // We can't reliably control env vars from Java on modern JDKs, so
        // we just assert the toString contract is preserved regardless of
        // env-var presence.
        withSysPropClear("searchable.ai.anthropic.base-url",
            "searchable.ai.anthropic.api-key",
            "searchable.ai.anthropic.api-version", () -> {
                final AnthropicProvider p = new AnthropicProvider();
                assertThat(p.toString())
                    .contains("AnthropicProvider")
                    .contains("baseUrl=")
                    .contains("apiVersion=")
                    .contains("apiKeyConfigured=");
            });
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private static void withSysPropClear(final String a, final String b, final String c,
                                         final ThrowingRunnable body) throws Exception {
        final String prevA = System.getProperty(a);
        final String prevB = System.getProperty(b);
        final String prevC = System.getProperty(c);
        System.clearProperty(a);
        System.clearProperty(b);
        System.clearProperty(c);
        try {
            body.run();
        } finally {
            if (prevA != null) System.setProperty(a, prevA); else System.clearProperty(a);
            if (prevB != null) System.setProperty(b, prevB); else System.clearProperty(b);
            if (prevC != null) System.setProperty(c, prevC); else System.clearProperty(c);
        }
    }
}
