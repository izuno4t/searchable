package com.worksap.nlp.lucene.sudachi.ja;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;

/**
 * Test-scope stand-in for {@code com.worksap.nlp.lucene.sudachi.ja.SudachiAnalyzer}.
 *
 * <p>SudachiAnalyzerFactory uses {@code Class.forName(SUDACHI_ANALYZER_CLASS)}
 * to lazily discover the real Sudachi analyzer. Bundling the actual
 * library + dictionary (~70 MB) just to exercise the factory is wasteful,
 * so this stub sits at the same fully-qualified name on the test
 * classpath. Test classloaders find it first; production classpaths never
 * contain it because it lives under {@code src/test/java}.
 *
 * <p>Construction behavior is controlled by the system property
 * {@code stub.sudachi.mode}:
 * <ul>
 *   <li>unset / {@code ok} — successful no-arg construction (happy path)</li>
 *   <li>{@code throw} — constructor throws so the factory's
 *       {@link java.lang.reflect.InvocationTargetException} catch fires</li>
 * </ul>
 */
public final class SudachiAnalyzer extends Analyzer {

    public SudachiAnalyzer() {
        if ("throw".equals(System.getProperty("stub.sudachi.mode"))) {
            throw new IllegalStateException("simulated Sudachi init failure");
        }
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName) {
        // Delegate to a minimal real analyzer so the returned TokenStream is
        // usable; we never actually call this from coverage tests.
        final Analyzer delegate = new StandardAnalyzer();
        try (var stream = delegate.tokenStream(fieldName, "")) {
            // Force one read so resources are exercised.
        } catch (Exception ignored) {
            // best-effort
        }
        // Re-create through StandardAnalyzer's own components.
        final Tokenizer tokenizer = new org.apache.lucene.analysis.standard.StandardTokenizer();
        return new TokenStreamComponents(tokenizer, (TokenStream) tokenizer);
    }
}
