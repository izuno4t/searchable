package io.searchable.core.application.config;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import io.searchable.core.infrastructure.lucene.AnalyzerType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchableGlobalConfigTest {

    @Test
    void defaultsExposesKuromoji() {
        final SearchableGlobalConfig cfg = SearchableGlobalConfig.defaults();
        assertThat(cfg.analyzer()).isEqualTo(AnalyzerType.KUROMOJI);
        assertThat(cfg.defaultArchitecture()).isEqualTo(SearchType.FULL_TEXT);
        assertThat(cfg.defaultSearchStrategy()).isEqualTo(SearchStrategy.SEQUENTIAL);
        assertThat(cfg.defaultSearchOrder()).isEqualTo(SearchOrder.FULL_TEXT_FIRST);
    }

    @Test
    void backwardCompatibleConstructorDefaultsToKuromoji() {
        final SearchableGlobalConfig cfg = new SearchableGlobalConfig(
            SearchType.HYBRID, SearchStrategy.PARALLEL, SearchOrder.VECTOR_FIRST);
        assertThat(cfg.analyzer()).isEqualTo(AnalyzerType.KUROMOJI);
    }

    @Test
    void nullAnalyzerBecomesKuromoji() {
        final SearchableGlobalConfig cfg = new SearchableGlobalConfig(
            SearchType.HYBRID, SearchStrategy.PARALLEL, SearchOrder.VECTOR_FIRST, null);
        assertThat(cfg.analyzer()).isEqualTo(AnalyzerType.KUROMOJI);
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() ->
            new SearchableGlobalConfig(null, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST, AnalyzerType.KUROMOJI))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            new SearchableGlobalConfig(SearchType.FULL_TEXT, null, SearchOrder.FULL_TEXT_FIRST, AnalyzerType.KUROMOJI))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            new SearchableGlobalConfig(SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, null, AnalyzerType.KUROMOJI))
            .isInstanceOf(NullPointerException.class);
    }
}
