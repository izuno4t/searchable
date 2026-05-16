package io.searchable.core.infrastructure.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzerTypeTest {

    @Test
    void parsesNullAndBlankAsKuromoji() {
        assertThat(AnalyzerType.from(null)).isEqualTo(AnalyzerType.KUROMOJI);
        assertThat(AnalyzerType.from("")).isEqualTo(AnalyzerType.KUROMOJI);
    }

    @Test
    void parsesCaseInsensitiveValues() {
        assertThat(AnalyzerType.from("kuromoji")).isEqualTo(AnalyzerType.KUROMOJI);
        assertThat(AnalyzerType.from("Sudachi")).isEqualTo(AnalyzerType.SUDACHI);
    }

    @Test
    void factoryForKuromojiReturnsJapaneseAnalyzer() {
        final AnalyzerFactory factory = AnalyzerFactory.forType(AnalyzerType.KUROMOJI);
        try (Analyzer a = factory.create("ns")) {
            assertThat(a).isInstanceOf(JapaneseAnalyzer.class);
        }
    }

    @Test
    void sudachiFactoryFallsBackToJapaneseAnalyzerWhenClasspathMissing() {
        // Sudachi is not on the test classpath, so the factory must
        // fall back to JapaneseAnalyzer instead of throwing.
        final AnalyzerFactory factory = AnalyzerFactory.forType(AnalyzerType.SUDACHI);
        try (Analyzer a = factory.create("ns")) {
            assertThat(a).isInstanceOf(JapaneseAnalyzer.class);
        }
    }
}
