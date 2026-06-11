package io.searchable.ai.ollama;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the OpenAI/Anthropic env-defaults tests for Ollama — covers the
 * {@code System.getProperty == null} fall-through inside
 * {@code resolveBaseUrl} and {@code resolveDefaultModel}.
 */
class OllamaProviderEnvDefaultsTest {

    @Test
    void defaultConstructorUsesLocalhostAndDefaultModel() throws Exception {
        withSysPropClear("searchable.ai.ollama.base-url",
            "searchable.ai.ollama.default-model", () -> {
                final OllamaProvider p = new OllamaProvider();
                assertThat(p.toString()).contains("localhost:11434");
                assertThat(p.toString()).contains("defaultModel=llama3.2");
            });
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private static void withSysPropClear(final String a, final String b,
                                         final ThrowingRunnable body) throws Exception {
        final String prevA = System.getProperty(a);
        final String prevB = System.getProperty(b);
        System.clearProperty(a);
        System.clearProperty(b);
        try {
            body.run();
        } finally {
            if (prevA != null) System.setProperty(a, prevA); else System.clearProperty(a);
            if (prevB != null) System.setProperty(b, prevB); else System.clearProperty(b);
        }
    }
}
