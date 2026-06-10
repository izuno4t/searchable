package io.searchable.core.application.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalConfigProviderTest {

    @Test
    void exposesInitialAndUpdates() {
        final GlobalConfig initial = GlobalConfig.defaults();
        final GlobalConfigProvider provider = new GlobalConfigProvider(initial);

        assertThat(provider.current()).isSameAs(initial);

        final GlobalConfig next = new GlobalConfig(
            io.searchable.core.domain.search.SearchType.HYBRID,
            io.searchable.core.domain.search.SearchStrategy.PARALLEL,
            io.searchable.core.domain.search.SearchOrder.VECTOR_FIRST);
        provider.update(next);
        assertThat(provider.current()).isSameAs(next);
    }

    @Test
    void rejectsNullInitialOrUpdate() {
        assertThatThrownBy(() -> new GlobalConfigProvider(null))
            .isInstanceOf(NullPointerException.class);
        final GlobalConfigProvider provider = new GlobalConfigProvider(GlobalConfig.defaults());
        assertThatThrownBy(() -> provider.update(null))
            .isInstanceOf(NullPointerException.class);
    }
}
