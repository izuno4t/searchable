package io.searchable.core.domain.namespace;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceConfigTest {

    @Test
    void defaultsFavorFullTextSequential() {
        final NamespaceConfig c = NamespaceConfig.defaults();
        assertThat(c.architecture()).isEqualTo(SearchType.FULL_TEXT);
        assertThat(c.searchStrategy()).isEqualTo(SearchStrategy.SEQUENTIAL);
        assertThat(c.searchOrder()).isEqualTo(SearchOrder.FULL_TEXT_FIRST);
        assertThat(c.aiConfig().enabled()).isFalse();
        assertThat(c.customParams()).isEmpty();
    }

    @Test
    void customParamsAreDefensivelyCopied() {
        final Map<String, Object> mutable = new HashMap<>();
        mutable.put("foo", 1);
        final NamespaceConfig c = new NamespaceConfig(
            SearchType.HYBRID, SearchStrategy.PARALLEL,
            SearchOrder.FULL_TEXT_FIRST, null, AiConfig.disabled(), mutable);

        mutable.put("bar", 2);
        assertThat(c.customParams()).containsOnlyKeys("foo");
    }

    @Test
    void customParamsAreImmutable() {
        final NamespaceConfig c = NamespaceConfig.defaults();
        assertThatThrownBy(() -> c.customParams().put("k", "v"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void architectureFlagsReflectEnum() {
        final NamespaceConfig fullText = NamespaceConfig.defaults();
        final NamespaceConfig hybrid = new NamespaceConfig(
            SearchType.HYBRID, SearchStrategy.PARALLEL,
            SearchOrder.FULL_TEXT_FIRST, null, AiConfig.disabled(), null);

        assertThat(fullText.isFullTextEnabled()).isTrue();
        assertThat(fullText.isVectorEnabled()).isFalse();
        assertThat(hybrid.isFullTextEnabled()).isTrue();
        assertThat(hybrid.isVectorEnabled()).isTrue();
    }
}
