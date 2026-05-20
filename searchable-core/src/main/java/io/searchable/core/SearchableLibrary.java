package io.searchable.core;

import io.searchable.core.application.DocumentBrowser;
import io.searchable.core.application.HybridSearchOrchestrator;
import io.searchable.core.application.IndexService;
import io.searchable.core.application.IndexStatisticsService;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.SearchResultEnricher;
import io.searchable.core.application.SearchService;
import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.application.config.GlobalConfigProvider;
import io.searchable.core.domain.dictionary.UserDictionaryRepository;
import io.searchable.core.domain.dictionary.UserDictionaryResolver;
import io.searchable.core.domain.document.DocumentMetadataRepository;
import io.searchable.core.domain.document.DocumentSourceRepository;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.index.IndexMetadataRepository;
import io.searchable.core.domain.namespace.NamespaceRepository;
import io.searchable.core.infrastructure.dictionary.JdbcUserDictionaryRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentSourceRepository;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import io.searchable.core.infrastructure.lucene.UserDictionaryAnalyzerFactory;
import io.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import io.searchable.core.infrastructure.plugin.PluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Top-level facade for embedding the Searchable library.
 *
 * <p>Wires together persistence (JDBC repositories), Lucene infrastructure,
 * and the application-layer services from a single {@link ApplicationConfig}.
 * The {@link Builder} allows individual collaborators to be overridden for
 * tests or advanced setups.
 *
 * <p>Owned {@link AutoCloseable} resources (Lucene index provider, plugin
 * loader, embedding provider, hybrid orchestrator) are released by
 * {@link #close()} in reverse order of acquisition.
 */
public final class SearchableLibrary implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SearchableLibrary.class);
    private static final int DEFAULT_EMBEDDING_DIMENSION = 128;

    private final ApplicationConfig configuration;
    private final NamespaceRepository namespaceRepository;
    private final IndexMetadataRepository indexMetadataRepository;
    private final UserDictionaryRepository dictionaryRepository;
    private final DocumentSourceRepository documentSourceRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final GlobalConfigProvider globalConfigProvider;
    private final EmbeddingProvider embeddingProvider;
    private final LuceneIndexProvider indexProvider;
    private final LuceneIndexer indexer;
    private final HybridSearchOrchestrator hybridOrchestrator;
    private final SearchService searchService;
    private final IndexService indexService;
    private final NamespaceService namespaceService;
    private final IndexStatisticsService statisticsService;
    private final DocumentBrowser documentBrowser;
    private final PluginLoader pluginLoader;
    private final boolean readOnly;
    private final Deque<AutoCloseable> closeables;

    private SearchableLibrary(final Builder b) {
        this.readOnly = b.readOnly;
        this.configuration = b.applicationConfig;
        this.namespaceRepository = b.namespaceRepository;
        this.indexMetadataRepository = b.indexMetadataRepository;
        this.dictionaryRepository = b.dictionaryRepository;
        this.documentSourceRepository = b.documentSourceRepository;
        this.documentMetadataRepository = b.documentMetadataRepository;
        this.globalConfigProvider = b.globalConfigProvider;
        this.embeddingProvider = b.embeddingProvider;
        this.indexProvider = b.indexProvider;
        this.indexer = b.indexer;
        this.hybridOrchestrator = b.hybridOrchestrator;
        this.searchService = b.searchService;
        this.indexService = b.indexService;
        this.namespaceService = b.namespaceService;
        this.statisticsService = b.statisticsService;
        this.documentBrowser = b.documentBrowser;
        this.pluginLoader = b.pluginLoader;
        this.closeables = b.closeables;
    }

    public ApplicationConfig configuration() {
        return configuration;
    }

    public NamespaceRepository namespaceRepository() {
        return namespaceRepository;
    }

    public IndexMetadataRepository indexMetadataRepository() {
        return indexMetadataRepository;
    }

    public UserDictionaryRepository dictionaryRepository() {
        return dictionaryRepository;
    }

    public DocumentSourceRepository documentSourceRepository() {
        return documentSourceRepository;
    }

    public DocumentMetadataRepository documentMetadataRepository() {
        return documentMetadataRepository;
    }

    public GlobalConfigProvider globalConfigProvider() {
        return globalConfigProvider;
    }

    public EmbeddingProvider embeddingProvider() {
        return embeddingProvider;
    }

    public LuceneIndexProvider indexProvider() {
        return indexProvider;
    }

    public LuceneIndexer indexer() {
        return indexer;
    }

    public SearchService searchService() {
        return searchService;
    }

    /**
     * @throws IllegalStateException when the library was built in read-only mode
     */
    public IndexService indexService() {
        if (indexService == null) {
            throw new IllegalStateException("IndexService is not available in read-only mode");
        }
        return indexService;
    }

    /**
     * @throws IllegalStateException when the library was built in read-only mode
     */
    public NamespaceService namespaceService() {
        if (namespaceService == null) {
            throw new IllegalStateException("NamespaceService is not available in read-only mode");
        }
        return namespaceService;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public IndexStatisticsService indexStatisticsService() {
        return statisticsService;
    }

    public DocumentBrowser documentBrowser() {
        return documentBrowser;
    }

    public PluginLoader pluginLoader() {
        return pluginLoader;
    }

    public HybridSearchOrchestrator hybridSearchOrchestrator() {
        return hybridOrchestrator;
    }

    @Override
    public void close() {
        while (!closeables.isEmpty()) {
            final AutoCloseable c = closeables.pop();
            try {
                c.close();
            } catch (Exception e) {
                log.warn("Failed to close {}", c.getClass().getSimpleName(), e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Bootstraps a library instance from configuration alone, applying all
     * sensible defaults (Hash embedding, JDBC repositories, file-system
     * Lucene directory, schema auto-initialization).
     */
    public static SearchableLibrary fromConfig(final ApplicationConfig config) {
        return builder().applicationConfig(config).build();
    }

    /**
     * Step-by-step builder. Required: {@code applicationConfig(...)}.
     * Everything else has a default; supply an override only when the
     * default does not fit (e.g. injecting an in-memory repository for
     * tests, or swapping in an ONNX embedding provider).
     */
    public static final class Builder {

        private ApplicationConfig applicationConfig;
        private DataSource dataSource;
        private boolean initializeSchema = true;
        private boolean readOnly = false;
        private NamespaceRepository namespaceRepository;
        private IndexMetadataRepository indexMetadataRepository;
        private UserDictionaryRepository dictionaryRepository;
        private DocumentSourceRepository documentSourceRepository;
        private DocumentMetadataRepository documentMetadataRepository;
        private GlobalConfigProvider globalConfigProvider;
        private EmbeddingProvider embeddingProvider;
        private AnalyzerFactory analyzerFactory;
        private LuceneIndexProvider indexProvider;
        private LuceneIndexer indexer;
        private HybridSearchOrchestrator hybridOrchestrator;
        private SearchService searchService;
        private IndexService indexService;
        private NamespaceService namespaceService;
        private IndexStatisticsService statisticsService;
        private DocumentBrowser documentBrowser;
        private PluginLoader pluginLoader;
        private Clock clock = Clock.systemUTC();

        private final Deque<AutoCloseable> closeables = new ArrayDeque<>();

        private Builder() { }

        public Builder applicationConfig(final ApplicationConfig config) {
            this.applicationConfig = Objects.requireNonNull(config, "config must not be null");
            return this;
        }

        public Builder dataSource(final DataSource ds) {
            this.dataSource = ds;
            return this;
        }

        public Builder initializeSchema(final boolean enabled) {
            this.initializeSchema = enabled;
            return this;
        }

        /**
         * Enable read-only mode. The metadata database is opened normally
         * (callers may still need to read namespace metadata), but the
         * Lucene index provider refuses to create writers, and all write
         * services ({@link IndexService}, {@link NamespaceService}) reject
         * mutating calls with {@link IllegalStateException}.
         */
        public Builder readOnly(final boolean enabled) {
            this.readOnly = enabled;
            return this;
        }

        public Builder namespaceRepository(final NamespaceRepository repo) {
            this.namespaceRepository = repo;
            return this;
        }

        public Builder indexMetadataRepository(final IndexMetadataRepository repo) {
            this.indexMetadataRepository = repo;
            return this;
        }

        public Builder dictionaryRepository(final UserDictionaryRepository repo) {
            this.dictionaryRepository = repo;
            return this;
        }

        public Builder documentSourceRepository(final DocumentSourceRepository repo) {
            this.documentSourceRepository = repo;
            return this;
        }

        public Builder documentMetadataRepository(final DocumentMetadataRepository repo) {
            this.documentMetadataRepository = repo;
            return this;
        }

        public Builder globalConfig(final GlobalConfig globalConfig) {
            this.globalConfigProvider = new GlobalConfigProvider(globalConfig);
            return this;
        }

        public Builder globalConfigProvider(final GlobalConfigProvider provider) {
            this.globalConfigProvider = provider;
            return this;
        }

        public Builder embeddingProvider(final EmbeddingProvider provider) {
            this.embeddingProvider = provider;
            return this;
        }

        public Builder analyzerFactory(final AnalyzerFactory factory) {
            this.analyzerFactory = factory;
            return this;
        }

        public Builder pluginLoader(final PluginLoader loader) {
            this.pluginLoader = loader;
            return this;
        }

        public Builder clock(final Clock clk) {
            this.clock = Objects.requireNonNull(clk, "clock must not be null");
            return this;
        }

        public SearchableLibrary build() {
            Objects.requireNonNull(applicationConfig, "applicationConfig is required");

            // Persistence (DataSource + repositories).
            if (dataSource == null) {
                dataSource = DataSourceFactory.create(applicationConfig.persistence(), readOnly);
                registerCloseable(() -> {
                    if (dataSource instanceof AutoCloseable c) {
                        c.close();
                    }
                });
            }
            if (initializeSchema) {
                new SchemaInitializer(dataSource).initialize();
            }
            if (namespaceRepository == null) {
                namespaceRepository = new JdbcNamespaceRepository(dataSource);
            }
            if (indexMetadataRepository == null) {
                indexMetadataRepository = new JdbcIndexMetadataRepository(dataSource);
            }
            if (dictionaryRepository == null) {
                dictionaryRepository = new JdbcUserDictionaryRepository(dataSource);
            }
            if (documentSourceRepository == null) {
                documentSourceRepository = new JdbcDocumentSourceRepository(dataSource);
            }
            if (documentMetadataRepository == null) {
                documentMetadataRepository = new JdbcDocumentMetadataRepository(dataSource);
            }
            if (globalConfigProvider == null) {
                globalConfigProvider = new GlobalConfigProvider(applicationConfig.global());
            }

            // Lucene + analyzer.
            if (analyzerFactory == null) {
                final var analyzerType = applicationConfig.global().analyzer();
                analyzerFactory = analyzerType == io.searchable.core.infrastructure.lucene.AnalyzerType.KUROMOJI
                    ? new UserDictionaryAnalyzerFactory(
                        new UserDictionaryResolver(dictionaryRepository))
                    : AnalyzerFactory.forType(analyzerType);
            }
            if (indexProvider == null) {
                indexProvider = new LuceneIndexProvider(
                    new IndexLayout(applicationConfig.index().directory()),
                    analyzerFactory,
                    readOnly,
                    applicationConfig.index().backend());
                registerCloseable(indexProvider);
            }

            // Embedding.
            if (embeddingProvider == null) {
                embeddingProvider = new HashEmbeddingProvider(DEFAULT_EMBEDDING_DIMENSION);
                registerCloseable(embeddingProvider);
            }

            if (indexer == null && !readOnly) {
                indexer = new LuceneIndexer(indexProvider, embeddingProvider);
            }

            // Search side.
            final LuceneFullTextSearcher fullText = new LuceneFullTextSearcher(indexProvider);
            final LuceneVectorSearcher vector = new LuceneVectorSearcher(indexProvider, embeddingProvider);

            if (hybridOrchestrator == null) {
                hybridOrchestrator = new HybridSearchOrchestrator(fullText, vector);
                registerCloseable(hybridOrchestrator);
            }
            if (searchService == null) {
                searchService = new SearchService(namespaceRepository, fullText, vector,
                    hybridOrchestrator,
                    new SearchResultEnricher(documentMetadataRepository));
            }
            if (indexService == null && !readOnly) {
                indexService = new IndexService(
                    namespaceRepository, indexMetadataRepository, indexProvider, indexer,
                    documentSourceRepository, documentMetadataRepository, clock);
            }
            if (namespaceService == null && !readOnly) {
                namespaceService = new NamespaceService(
                    namespaceRepository, indexMetadataRepository, indexProvider,
                    globalConfigProvider, clock);
            }
            if (statisticsService == null) {
                statisticsService = new IndexStatisticsService(namespaceRepository, indexMetadataRepository);
            }
            if (documentBrowser == null) {
                documentBrowser = new DocumentBrowser(documentMetadataRepository);
            }
            if (pluginLoader == null) {
                pluginLoader = new PluginLoader(applicationConfig.plugins().directory());
                registerCloseable(pluginLoader);
            }

            log.info("SearchableLibrary initialized (dataDirectory={}, indexBackend={}, indexDirectory={}, persistence={}, readOnly={})",
                applicationConfig.dataDirectory(),
                applicationConfig.index().backend(),
                applicationConfig.index().directory(),
                applicationConfig.persistence().type(),
                readOnly);

            return new SearchableLibrary(this);
        }

        private void registerCloseable(final AutoCloseable closeable) {
            if (closeable != null) {
                closeables.push(closeable);
            }
        }
    }
}
