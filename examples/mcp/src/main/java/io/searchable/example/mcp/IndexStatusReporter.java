package io.searchable.example.mcp;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.IndexStatisticsService.NamespaceEntry;
import io.searchable.core.application.IndexStatisticsService.StatusSnapshot;
import io.searchable.core.util.AnsiText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * Logs an index status banner at startup and again whenever a
 * SIGHUP-triggered refresh completes. Lets operators see which data
 * directory the MCP server opened and watch reload counts grow.
 *
 * <p>Output goes through SLF4J. The MCP server uses stdio for protocol
 * traffic, so Logback must be configured to route logs to stderr or a
 * file — see {@code logback.xml}.
 */
final class IndexStatusReporter {

    private static final Logger log = LoggerFactory.getLogger(IndexStatusReporter.class);
    private static final String DIVIDER = "=".repeat(60);

    private final SearchableLibrary library;

    IndexStatusReporter(final SearchableLibrary library) {
        this.library = library;
    }

    void reportStartup() {
        printSummary("MCP READY");
    }

    void reportReload() {
        printSummary("MCP RELOADED");
    }

    private void printSummary(final String banner) {
        final StatusSnapshot snap = library.indexStatisticsService().snapshot();
        final var stats = snap.aggregate();
        final String dataDir = library.configuration().dataDirectory().toString();
        final String lastUpdated = stats.lastUpdated() == null
            ? "(no data)" : stats.lastUpdated().toString();
        final String perNamespace = renderPerNamespace(snap);

        log.info("""

                %s
                  %s %s  --  data: %s
                %s
                  Namespaces   : %s
                  Documents    : %s
                  Index size   : %s  %s
                  Last updated : %s
                ------------------------------------------------------------
                  Per namespace:
                %s
                %s
                """.formatted(
                    AnsiText.green(DIVIDER),
                    AnsiText.green("[OK]"),
                    AnsiText.bold(banner),
                    AnsiText.bold(dataDir),
                    AnsiText.green(DIVIDER),
                    AnsiText.green(String.valueOf(stats.namespaceCount())),
                    AnsiText.green(String.valueOf(stats.documentCount())),
                    AnsiText.green(humanBytes(stats.indexSizeBytes())),
                    AnsiText.dim("(" + stats.indexSizeBytes() + " bytes)"),
                    lastUpdated,
                    perNamespace,
                    AnsiText.green(DIVIDER)));
    }

    private static String renderPerNamespace(final StatusSnapshot snap) {
        if (snap.perNamespace().isEmpty()) {
            return "    (none)";
        }
        return snap.perNamespace().stream()
            .map(IndexStatusReporter::renderNamespaceLine)
            .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String renderNamespaceLine(final NamespaceEntry e) {
        return String.format("    %-20s :  %d docs  /  %s",
            e.namespaceId(), e.documentCount(), humanBytes(e.indexSizeBytes()));
    }

    private static String humanBytes(final long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes;
        int unit = 0;
        while (v >= 1024 && unit < 4) {
            v /= 1024;
            unit++;
        }
        return String.format("%.2f %sB", v, "KMGTP".charAt(unit - 1));
    }
}
