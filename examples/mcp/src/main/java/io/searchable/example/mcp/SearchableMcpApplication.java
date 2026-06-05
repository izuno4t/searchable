package io.searchable.example.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.ConfigLoader;
import io.searchable.core.infrastructure.runtime.PidFile;
import io.searchable.core.infrastructure.runtime.SighupListener;
import io.searchable.example.mcp.config.McpCapabilitiesConfig;
import io.searchable.example.mcp.config.McpCapabilitiesLoader;
import io.searchable.example.mcp.tool.SearchDocumentsTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Main entry point for the Searchable MCP server (stdio transport).
 *
 * <p>Opens the library in read-only mode and registers a {@code SIGHUP}
 * handler so the CLI can hot-swap the index after an ingest without
 * the server being restarted.
 *
 * <p>Configuration is read from a YAML file specified by
 * {@code --config <path>} (defaults to {@code ./searchable.yaml}).
 */
public final class SearchableMcpApplication {

    private static final Logger log = LoggerFactory.getLogger(SearchableMcpApplication.class);
    private static final String APP_NAME = "mcp";

    private SearchableMcpApplication() { }

    public static void main(final String[] args) throws Exception {
        final Path configPath = resolveConfigPath(args);
        log.info("loading config from {}", configPath);
        final ApplicationConfig config = new ConfigLoader().load(configPath);

        final McpCapabilitiesConfig capabilities = loadCapabilities(args);

        try (SearchableLibrary library = SearchableLibrary.builder()
                .applicationConfig(config)
                .readOnly(true)
                .build();
             PidFile pidFile = PidFile.open(config.dataDirectory(), APP_NAME)) {

            final IndexStatusReporter reporter = new IndexStatusReporter(library);
            final SighupListener listener = SighupListener.install(() -> {
                final int n = library.refresh();
                log.info("SIGHUP received: refreshed {} namespace(s)", n);
                reporter.reportReload();
            });
            if (!listener.isInstalled()) {
                log.warn("mcp will not auto-refresh on CLI ingest "
                    + "(SIGHUP unavailable on this platform)");
            }

            final ObjectMapper objectMapper = newObjectMapper();
            final McpServer server = new McpServer(objectMapper, List.of(
                new SearchDocumentsTool(library.searchService(), objectMapper),
                new io.searchable.example.mcp.tool.GetDocumentTool(
                    library.documentBrowser(), objectMapper)),
                capabilities);

            try {
                log.info("searchable-mcp ready (stdio, pid={})", pidFile.pid());
                reporter.reportStartup();
                server.serve(System.in, System.out);
            } finally {
                listener.uninstall();
            }
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
