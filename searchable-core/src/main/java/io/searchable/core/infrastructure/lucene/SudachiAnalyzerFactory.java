package io.searchable.core.infrastructure.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * {@link AnalyzerFactory} backed by Sudachi.
 *
 * <p>The Sudachi library is intentionally an <em>optional</em> runtime
 * dependency (the project does not include it as a Maven dep). When the
 * application classpath provides {@code com.worksap.nlp.lucene.sudachi.ja.SudachiAnalyzer}
 * it is instantiated via reflection; otherwise the factory logs a warning
 * and falls back to {@link JapaneseAnalyzer} so that misconfigured
 * deployments stay searchable instead of failing to start.
 *
 * <p>Deployment notes for callers:
 * <ul>
 *   <li>Add {@code com.worksap.nlp:lucene-analyzers-sudachi}
 *       (matching the active Lucene version) to your application POM.</li>
 *   <li>Bundle a Sudachi system dictionary (e.g. {@code system_core.dic})
 *       and point the {@code sudachi.dictionary.path} JVM property at it
 *       — or follow the dictionary-resolution rules of the version you
 *       picked.</li>
 * </ul>
 */
public final class SudachiAnalyzerFactory implements AnalyzerFactory {

    private static final Logger log = LoggerFactory.getLogger(SudachiAnalyzerFactory.class);
    private static final String SUDACHI_ANALYZER_CLASS =
        "com.worksap.nlp.lucene.sudachi.ja.SudachiAnalyzer";

    @Override
    public Analyzer create(final String namespaceId) {
        try {
            final Class<?> klass = Class.forName(SUDACHI_ANALYZER_CLASS);
            return (Analyzer) klass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            log.warn("Sudachi analyzer ({}) is not on the classpath for namespace {};"
                + " falling back to JapaneseAnalyzer (Kuromoji). Add"
                + " com.worksap.nlp:lucene-analyzers-sudachi to enable Sudachi.",
                SUDACHI_ANALYZER_CLASS, namespaceId);
            return new JapaneseAnalyzer();
        } catch (InvocationTargetException | InstantiationException
                 | IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalStateException(
                "Failed to instantiate " + SUDACHI_ANALYZER_CLASS
                    + " for namespace " + namespaceId, e);
        }
    }
}
