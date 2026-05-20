package io.searchable.example.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.searchable.core.application.HybridSearchOrchestrator;
import io.searchable.core.application.SearchService;
import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.ConfigLoader;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import io.searchable.example.mcp.config.McpCapabilitiesConfig;
import io.searchable.example.mcp.config.McpCapabilitiesLoader;
import io.searchable.example.mcp.tool.SearchDocumentsTool;
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

        final McpCapabilitiesConfig capabilities = loadCapabilities(args);

        final DataSource dataSource = DataSourceFactory.create(config.persistence());
        new SchemaInitializer(dataSource).initialize();

        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(config.index().directory()), AnalyzerFactory.japanese());
             EmbeddingProvider embedding = new HashEmbeddingProvider(384);
             HybridSearchOrchestrator hybrid = new HybridSearchOrchestrator(
                 new LuceneFullTextSearcher(provider),
                 new LuceneVectorSearcher(provider, embedding))) {

            final io.searchable.core.domain.document.DocumentMetadataRepository metadataRepo =
                new io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentMetadataRepository(
                    dataSource);

            final SearchService searchService = new SearchService(
                new JdbcNamespaceRepository(dataSource),
                new LuceneFullTextSearcher(provider),
                new LuceneVectorSearcher(provider, embedding),
                hybrid,
                new io.searchable.core.application.SearchResultEnricher(metadataRepo));

            final ObjectMapper objectMapper = newObjectMapper();
            final io.searchable.core.application.DocumentBrowser documentBrowser =
                new io.searchable.core.application.DocumentBrowser(metadataRepo);
            final McpServer server = new McpServer(objectMapper, List.of(
                new SearchDocumentsTool(searchService, objectMapper),
                new io.searchable.example.mcp.tool.GetDocumentTool(documentBrowser, objectMapper)),
                capabilities);

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

    /**
     * Resolve the MCP capabilities source in this order:
     * <ol>
     *   <li>{@code --mcp-capabilities <path>} CLI argument</li>
     *   <li>{@code ./mcp-capabilities.yaml} in the working directory</li>
     * </ol>
     * Fails fast if neither is available — the sample file is checked into
     * the {@code examples/mcp} module and is intentionally not packaged
     * inside the JAR.
     */
    static McpCapabilitiesConfig loadCapabilities(final String[] args) {
        final McpCapabilitiesLoader loader = new McpCapabilitiesLoader();
        for (int i = 0; i < args.length - 1; i++) {
            if ("--mcp-capabilities".equals(args[i])) {
                final Path explicit = Path.of(args[i + 1]);
                log.info("loading MCP capabilities from {}", explicit);
                return loader.load(explicit);
            }
        }
        final Path cwd = Path.of("./mcp-capabilities.yaml");
        if (Files.exists(cwd)) {
            log.info("loading MCP capabilities from {}", cwd);
            return loader.load(cwd);
        }
        throw new IllegalStateException(
            "No MCP capabilities file found. Pass --mcp-capabilities <path> or place "
            + "mcp-capabilities.yaml in the working directory. "
            + "See examples/mcp/mcp-capabilities.yaml for a sample.");
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
