package com.searchable.api.config;

import com.searchable.core.application.DocumentBrowser;
import com.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link DocumentBrowser} bean. Kept in a separate configuration
 * class so that the main {@code SearchableConfiguration} stays focused on
 * core search wiring.
 */
@Configuration
public class IndexBrowserConfig {

    @Bean
    public DocumentBrowser documentBrowser(final LuceneIndexProvider provider) {
        return new DocumentBrowser(provider);
    }
}
