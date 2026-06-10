package io.searchable.core.application.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchableGlobalConfigProviderTest {

    @Test
    void exposesInitialAndUpdates() {
        final SearchableGlobalConfig initial = SearchableGlobalConfig.defaults();
        final SearchableGlobalConfigProvider provider = new SearchableGlobalConfigProvider(initial);

        assertThat(provider.current()).isSameAs(initial);

        final SearchableGlobalConfig next = new SearchableGlobalConfig(
            io.searchable.core.domain.search.SearchType.HYBRID,
            io.searchable.core.domain.search.SearchStrategy.PARALLEL,
            io.searchable.core.domain.search.SearchOrder.VECTOR_FIRST);
        provider.update(next);
        assertThat(provider.current()).isSameAs(next);
    }

    @Test
    void rejectsNullInitialOrUpdate() {
        assertThatThrownBy(() -> new SearchableGlobalConfigProvider(null))
            .isInstanceOf(NullPointerException.class);
        final SearchableGlobalConfigProvider provider = new SearchableGlobalConfigProvider(SearchableGlobalConfig.defaults());
        assertThatThrownBy(() -> provider.update(null))
            .isInstanceOf(NullPointerException.class);
    }
}
