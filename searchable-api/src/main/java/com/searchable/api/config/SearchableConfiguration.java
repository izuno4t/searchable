package com.searchable.api.config;

import com.searchable.core.application.IndexService;
import com.searchable.core.application.NamespaceService;
import com.searchable.core.application.SearchService;
import com.searchable.core.application.config.GlobalConfig;
import com.searchable.core.domain.index.IndexMetadataRepository;
import com.searchable.core.domain.namespace.NamespaceRepository;
import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;
import com.searchable.core.infrastructure.lucene.AnalyzerFactory;
import com.searchable.core.infrastructure.lucene.IndexLayout;
import com.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import com.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import com.searchable.core.infrastructure.lucene.LuceneIndexer;
import com.searchable.core.infrastructure.persistence.DataSourceFactory;
import com.searchable.core.infrastructure.persistence.PersistenceConfig;
import com.searchable.core.infrastructure.persistence.SchemaInitializer;
import com.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import com.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import com.searchable.core.infrastructure.plugin.PluginLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Wires the core services as Spring beans.
 */
@Configuration
@EnableConfigurationProperties(SearchableProperties.class)
public class SearchableConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DataSource dataSource(final SearchableProperties props) {
        final SearchableProperties.Persistence p = props.getPersistence();
        return DataSourceFactory.create(
            new PersistenceConfig(p.getType(), p.getUrl(), p.getUsername(), p.getPassword()));
    }

    @Bean(initMethod = "initialize")
    public SchemaInitializer schemaInitializer(final DataSource dataSource) {
        return new SchemaInitializer(dataSource);
    }

    @Bean
    public NamespaceRepository namespaceRepository(final DataSource dataSource,
                                                   final SchemaInitializer init) {
        // SchemaInitializer is referenced to ensure the schema is created before
        // any repository starts issuing queries.
        return new JdbcNamespaceRepository(dataSource);
    }

    @Bean
    public IndexMetadataRepository indexMetadataRepository(final DataSource dataSource,
                                                           final SchemaInitializer init) {
        return new JdbcIndexMetadataRepository(dataSource);
    }

    @Bean(destroyMethod = "close")
    public LuceneIndexProvider luceneIndexProvider(final SearchableProperties props) {
        return new LuceneIndexProvider(
            new IndexLayout(props.getIndex().getDirectory()),
            AnalyzerFactory.japanese());
    }

    @Bean
    public LuceneIndexer luceneIndexer(final LuceneIndexProvider provider) {
        return new LuceneIndexer(provider);
    }

    @Bean
    public LuceneFullTextSearcher luceneFullTextSearcher(final LuceneIndexProvider provider) {
        return new LuceneFullTextSearcher(provider);
    }

    @Bean(destroyMethod = "close")
    public PluginLoader pluginLoader(final SearchableProperties props) {
        return new PluginLoader(props.getPlugins().getDirectory());
    }

    @Bean
    public GlobalConfig globalConfig(final SearchableProperties props) {
        final SearchableProperties.Global g = props.getGlobal();
        return new GlobalConfig(
            SearchType.valueOf(g.getDefaultArchitecture()),
            SearchStrategy.valueOf(g.getDefaultSearchStrategy()),
            SearchOrder.valueOf(g.getDefaultSearchOrder())
        );
    }

    @Bean
    public NamespaceService namespaceService(final NamespaceRepository nr,
                                             final IndexMetadataRepository imr,
                                             final LuceneIndexProvider provider,
                                             final GlobalConfig global,
                                             final Clock clock) {
        return new NamespaceService(nr, imr, provider, global, clock);
    }

    @Bean
    public IndexService indexService(final NamespaceRepository nr,
                                     final IndexMetadataRepository imr,
                                     final LuceneIndexProvider provider,
                                     final LuceneIndexer indexer,
                                     final Clock clock) {
        return new IndexService(nr, imr, provider, indexer, clock);
    }

    @Bean
    public SearchService searchService(final NamespaceRepository nr,
                                       final LuceneFullTextSearcher searcher) {
        return new SearchService(nr, searcher);
    }
}
