package io.searchable.core.domain.search;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchOptionsTest {

    @Test
    void defaultsExposesExpectedFieldValues() {
        final SearchOptions opts = SearchOptions.defaults();

        assertThat(opts.highlightEnabled()).isTrue();
        assertThat(opts.snippetLength()).isEqualTo(SearchOptions.DEFAULT_SNIPPET_LENGTH);
        assertThat(opts.escapeMarkup()).isTrue();
        assertThat(opts.lazyLoad()).isFalse();
        assertThat(opts.bm25K1()).isNull();
        assertThat(opts.bm25B()).isNull();
        assertThat(opts.metaWeights()).isEmpty();
    }

    @Test
    void singleArgConstructorMatchesDefaultsForOtherFields() {
        final SearchOptions opts = new SearchOptions(false);

        assertThat(opts.highlightEnabled()).isFalse();
        assertThat(opts.snippetLength()).isEqualTo(SearchOptions.DEFAULT_SNIPPET_LENGTH);
        assertThat(opts.escapeMarkup()).isTrue();
        assertThat(opts.lazyLoad()).isFalse();
        assertThat(opts.metaWeights()).isEmpty();
    }

    @Test
    void threeArgConstructorSetsHighlightingFields() {
        final SearchOptions opts = new SearchOptions(true, 50, false);

        assertThat(opts.snippetLength()).isEqualTo(50);
        assertThat(opts.escapeMarkup()).isFalse();
        assertThat(opts.lazyLoad()).isFalse();
    }

    @Test
    void fourArgConstructorEnablesLazyLoad() {
        final SearchOptions opts = new SearchOptions(true, 75, true, true);

        assertThat(opts.lazyLoad()).isTrue();
        assertThat(opts.snippetLength()).isEqualTo(75);
    }

    @Test
    void sixArgConstructorIncludesBm25Overrides() {
        final SearchOptions opts = new SearchOptions(true, 100, true, false, 1.5, 0.6);

        assertThat(opts.bm25K1()).isEqualTo(1.5);
        assertThat(opts.bm25B()).isEqualTo(0.6);
        assertThat(opts.metaWeights()).isEmpty();
    }

    @Test
    void nonPositiveSnippetLengthFallsBackToDefault() {
        assertThat(new SearchOptions(true, 0, true).snippetLength())
            .isEqualTo(SearchOptions.DEFAULT_SNIPPET_LENGTH);
        assertThat(new SearchOptions(true, -10, true).snippetLength())
            .isEqualTo(SearchOptions.DEFAULT_SNIPPET_LENGTH);
    }

    @Test
    void rejectsNegativeBm25K1() {
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, -0.1, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bm25K1");
    }

    @Test
    void rejectsNonFiniteBm25K1() {
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, Double.NaN, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, Double.POSITIVE_INFINITY, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBm25BOutsideZeroToOneRange() {
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, null, -0.01))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bm25B");
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, null, 1.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bm25B");
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, null, Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullMetaWeightsBecomesEmptyMap() {
        final SearchOptions opts = new SearchOptions(true, 100, true, false, null, null, null);
        assertThat(opts.metaWeights()).isEmpty();
    }

    @Test
    void metaWeightsAreDefensivelyCopied() {
        final Map<String, Double> mutable = new LinkedHashMap<>();
        mutable.put("title", 2.0);

        final SearchOptions opts = new SearchOptions(true, 100, true, false, null, null, mutable);
        mutable.put("title", 99.0);
        mutable.put("content", 5.0);

        assertThat(opts.metaWeights()).containsExactlyEntriesOf(Map.of("title", 2.0));
    }

    @Test
    void rejectsNonPositiveMetaWeight() {
        final Map<String, Double> weights = new HashMap<>();
        weights.put("title", 0.0);
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, null, null, weights))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("metaWeights[title]");
    }

    @Test
    void rejectsNonFiniteMetaWeight() {
        final Map<String, Double> weights = new HashMap<>();
        weights.put("title", Double.NaN);
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, null, null, weights))
            .isInstanceOf(IllegalArgumentException.class);

        final Map<String, Double> inf = new HashMap<>();
        inf.put("title", Double.POSITIVE_INFINITY);
        assertThatThrownBy(() -> new SearchOptions(true, 100, true, false, null, null, inf))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
