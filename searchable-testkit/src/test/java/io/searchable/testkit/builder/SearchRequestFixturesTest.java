package io.searchable.testkit.builder;

import io.searchable.core.domain.search.SearchRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRequestFixturesTest {

    @Test
    void singleArgBuilderTargetsDefaultNamespace() {
        final SearchRequest req = SearchRequestFixtures.builder("hello").build();

        assertThat(req.query()).isEqualTo("hello");
        assertThat(req.namespaceIds())
            .containsExactly(DocumentFixtures.DEFAULT_NAMESPACE);
    }

    @Test
    void twoArgBuilderRespectsCustomNamespace() {
        final SearchRequest req = SearchRequestFixtures.builder("世界", "ns-custom").build();

        assertThat(req.query()).isEqualTo("世界");
        assertThat(req.namespaceIds()).containsExactly("ns-custom");
    }
}
