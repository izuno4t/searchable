package io.searchable.core.application.config;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import io.searchable.core.infrastructure.lucene.AnalyzerType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalConfigTest {

    @Test
    void defaultsExposesKuromoji() {
        final GlobalConfig cfg = GlobalConfig.defaults();
        assertThat(cfg.analyzer()).isEqualTo(AnalyzerType.KUROMOJI);
        assertThat(cfg.defaultArchitecture()).isEqualTo(SearchType.FULL_TEXT);
        assertThat(cfg.defaultSearchStrategy()).isEqualTo(SearchStrategy.SEQUENTIAL);
        assertThat(cfg.defaultSearchOrder()).isEqualTo(SearchOrder.FULL_TEXT_FIRST);
    }

    @Test
    void backwardCompatibleConstructorDefaultsToKuromoji() {
        final GlobalConfig cfg = new GlobalConfig(
            SearchType.HYBRID, SearchStrategy.PARALLEL, SearchOrder.VECTOR_FIRST);
        assertThat(cfg.analyzer()).isEqualTo(AnalyzerType.KUROMOJI);
    }

    @Test
    void nullAnalyzerBecomesKuromoji() {
        final GlobalConfig cfg = new GlobalConfig(
            SearchType.HYBRID, SearchStrategy.PARALLEL, SearchOrder.VECTOR_FIRST, null);
        assertThat(cfg.analyzer()).isEqualTo(AnalyzerType.KUROMOJI);
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() ->
            new GlobalConfig(null, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST, AnalyzerType.KUROMOJI))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            new GlobalConfig(SearchType.FULL_TEXT, null, SearchOrder.FULL_TEXT_FIRST, AnalyzerType.KUROMOJI))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            new GlobalConfig(SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, null, AnalyzerType.KUROMOJI))
            .isInstanceOf(NullPointerException.class);
    }
}
