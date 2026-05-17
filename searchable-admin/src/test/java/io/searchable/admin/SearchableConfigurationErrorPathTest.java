package io.searchable.admin;

import io.searchable.admin.config.SearchableConfiguration;
import io.searchable.admin.config.SearchableProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bean-factory unhappy paths can be exercised by calling the @Bean methods directly. */
class SearchableConfigurationErrorPathTest {

    @Test
    void unsupportedChunkingStrategyRejected() {
        final SearchableConfiguration cfg = new SearchableConfiguration();
        final SearchableProperties props = new SearchableProperties();
        props.getChunking().setStrategy("unknown");
        assertThatThrownBy(() -> cfg.chunkingStrategy(props))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupportedEmbeddingProviderRejected() {
        final SearchableConfiguration cfg = new SearchableConfiguration();
        final SearchableProperties props = new SearchableProperties();
        props.getEmbedding().setProvider("unknown");
        assertThatThrownBy(() -> cfg.embeddingProvider(props))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupportedDictionaryStorageRejected() {
        final SearchableConfiguration cfg = new SearchableConfiguration();
        final SearchableProperties props = new SearchableProperties();
        props.getDictionary().setStorage("unknown");
        assertThatThrownBy(() -> cfg.userDictionaryRepository(props, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
