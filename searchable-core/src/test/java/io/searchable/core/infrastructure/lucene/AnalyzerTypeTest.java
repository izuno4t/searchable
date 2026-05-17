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
    void factoryForSudachiUsesSudachiAnalyzer() {
        // SudachiAnalyzerFactoryTest covers the classpath-missing fallback
        // path through a filtered classloader. Here we just verify the
        // factory hooks into the SUDACHI enum value and produces *some*
        // non-null analyzer using whatever is on the test classpath
        // (a test stub in this module).
        final AnalyzerFactory factory = AnalyzerFactory.forType(AnalyzerType.SUDACHI);
        try (Analyzer a = factory.create("ns")) {
            assertThat(a).isNotNull();
        }
    }
}
