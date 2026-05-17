package io.searchable.core.domain.namespace;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceConfigCoverageTest {

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new NamespaceConfig(
            null, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), 1.0, Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NamespaceConfig(
            SearchType.FULL_TEXT, null, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), 1.0, Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, null,
            null, AiConfig.disabled(), 1.0, Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, null, 1.0, Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidIndexWeight() {
        assertThatThrownBy(() -> new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), -0.1, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), Double.NaN, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), Double.POSITIVE_INFINITY, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backwardCompatibleConstructorAppliesDefaultIndexWeight() {
        final NamespaceConfig cfg = new NamespaceConfig(
            SearchType.HYBRID, SearchStrategy.PARALLEL, SearchOrder.VECTOR_FIRST,
            null, AiConfig.disabled(), Map.of());
        assertThat(cfg.indexWeight()).isEqualTo(NamespaceConfig.DEFAULT_INDEX_WEIGHT);
    }

    @Test
    void customParamsAreDefensivelyCopied() {
        final Map<String, Object> m = new HashMap<>();
        m.put("a", 1);
        final NamespaceConfig cfg = new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), 1.0, m);
        m.put("b", 2);
        assertThat(cfg.customParams()).containsOnlyKeys("a");
    }

    @Test
    void nullCustomParamsBecomesEmpty() {
        final NamespaceConfig cfg = new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), 1.0, null);
        assertThat(cfg.customParams()).isEmpty();
    }

    @Test
    void architectureFlagsForEachVariant() {
        final NamespaceConfig fullText = new NamespaceConfig(
            SearchType.FULL_TEXT, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), Map.of());
        final NamespaceConfig vector = new NamespaceConfig(
            SearchType.VECTOR, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), Map.of());
        final NamespaceConfig hybrid = new NamespaceConfig(
            SearchType.HYBRID, SearchStrategy.SEQUENTIAL, SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), Map.of());

        assertThat(fullText.isFullTextEnabled()).isTrue();
        assertThat(fullText.isVectorEnabled()).isFalse();

        assertThat(vector.isFullTextEnabled()).isFalse();
        assertThat(vector.isVectorEnabled()).isTrue();

        assertThat(hybrid.isFullTextEnabled()).isTrue();
        assertThat(hybrid.isVectorEnabled()).isTrue();
    }
}
