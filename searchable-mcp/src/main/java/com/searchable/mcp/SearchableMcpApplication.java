package com.searchable.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.searchable.core.application.HybridSearchOrchestrator;
import com.searchable.core.application.SearchService;
import com.searchable.core.application.config.ApplicationConfig;
import com.searchable.core.application.config.ConfigLoader;
import com.searchable.core.domain.embedding.EmbeddingProvider;
import com.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import com.searchable.core.infrastructure.lucene.AnalyzerFactory;
import com.searchable.core.infrastructure.lucene.IndexLayout;
import com.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import com.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import com.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import com.searchable.core.infrastructure.persistence.DataSourceFactory;
import com.searchable.core.infrastructure.persistence.SchemaInitializer;
import com.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import com.searchable.mcp.tool.SearchDocumentsTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Main entry point for the Searchable MCP server (stdio transport).
 *
 * <p>Wires the core services manually (no Spring) and runs the MCP
 * read/dispatch loop. Configuration is read from a YAML file specified by
 * {@code --config <path>} (defaults to {@code ./searchable.yaml}).
 */
public final class SearchableMcpApplication {

    private static final Logger log = LoggerFactory.getLogger(SearchableMcpApplication.class);

    private SearchableMcpApplication() { }

    public static void main(final String[] args) throws Exception {
        final Path configPath = resolveConfigPath(args);
        log.info("loading config from {}", configPath);
        final ApplicationConfig config = new ConfigLoader().load(configPath);

        final DataSource dataSource = DataSourceFactory.create(config.persistence());
        new SchemaInitializer(dataSource).initialize();

        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(config.index().directory()), AnalyzerFactory.japanese());
             EmbeddingProvider embedding = new HashEmbeddingProvider(384);
             HybridSearchOrchestrator hybrid = new HybridSearchOrchestrator(
                 new LuceneFullTextSearcher(provider),
                 new LuceneVectorSearcher(provider, embedding))) {

            final SearchService searchService = new SearchService(
                new JdbcNamespaceRepository(dataSource),
                new LuceneFullTextSearcher(provider),
                new LuceneVectorSearcher(provider, embedding),
                hybrid);

            final ObjectMapper objectMapper = newObjectMapper();
            final McpServer server = new McpServer(objectMapper,
                List.of(new SearchDocumentsTool(searchService, objectMapper)));

            log.info("searchable-mcp ready (stdio)");
            server.serve(System.in, System.out);
        }
    }

    private static Path resolveConfigPath(final String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        final Path fallback = Path.of("./searchable.yaml");
        if (!Files.exists(fallback)) {
            throw new IllegalStateException(
                "No configuration found. Pass --config <path> or place searchable.yaml in CWD.");
        }
        return fallback;
    }

    static ObjectMapper newObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    static InputStream stdin() {
        return System.in;
    }
}
