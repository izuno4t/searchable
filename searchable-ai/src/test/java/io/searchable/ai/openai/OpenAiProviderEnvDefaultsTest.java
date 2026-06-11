package io.searchable.ai.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@code System.getProperty == null} fall-through branches in
 * {@link OpenAiProvider#resolveBaseUrl()} and
 * {@link OpenAiProvider#resolveApiKey()} by constructing the provider with
 * no overrides set. The env-var branches inside the resolvers depend on
 * {@code OPENAI_BASE_URL} / {@code OPENAI_API_KEY} which we cannot mutate
 * from Java, so the env-true arms remain uncovered by design.
 */
class OpenAiProviderEnvDefaultsTest {

    @Test
    void defaultConstructorResolvesBaseUrlAndApiKey() throws Exception {
        // Clear searchable.ai.openai.* system properties so the resolvers
        // fall through to the env-var branch (or to defaults if neither is
        // set). We deliberately do NOT clear OPENAI_API_KEY / OPENAI_BASE_URL
        // because Java cannot mutate the JVM environment on modern JDKs; we
        // just assert the provider constructed successfully and the
        // toString contract is preserved regardless of env-key presence.
        withSysPropClear("searchable.ai.openai.base-url",
            "searchable.ai.openai.api-key", () -> {
                final OpenAiProvider p = new OpenAiProvider();
                assertThat(p.toString())
                    .contains("OpenAiProvider")
                    .contains("baseUrl=")
                    .contains("apiKeyConfigured=");
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
