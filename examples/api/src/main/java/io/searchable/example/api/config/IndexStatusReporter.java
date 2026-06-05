package io.searchable.example.api.config;

import io.searchable.core.application.IndexStatisticsService;
import io.searchable.core.application.IndexStatisticsService.NamespaceEntry;
import io.searchable.core.application.IndexStatisticsService.StatusSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Logs an index status banner at startup (after Spring's
 * {@code ApplicationReadyEvent}) and again whenever the SIGHUP-triggered
 * refresh in {@link SearchableConfiguration.ApiIndexHotReloadBridge}
 * completes. Lets operators spot data-directory mismatches and watch
 * reload counts grow.
 */
public class IndexStatusReporter {

    private static final Logger log = LoggerFactory.getLogger(IndexStatusReporter.class);

    private final IndexStatisticsService statistics;
    private final Path dataDirectory;

    public IndexStatusReporter(final IndexStatisticsService statistics, final Path dataDirectory) {
        this.statistics = statistics;
        this.dataDirectory = dataDirectory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        printSummary("API READY");
    }

    void reportReload() {
        printSummary("API RELOADED");
    }

    private void printSummary(final String banner) {
        final StatusSnapshot snap = statistics.snapshot();
        final var stats = snap.aggregate();
        final String lastUpdated = stats.lastUpdated() == null
            ? "(no data)" : stats.lastUpdated().toString();
        final String perNamespace = renderPerNamespace(snap);

        log.info("""

                [32m============================================================[0m
                  [32m[OK][0m [1m%s[0m  --  data: [1m%s[0m
                [32m============================================================[0m
                  Namespaces   : [32m%d[0m
                  Documents    : [32m%d[0m
                  Index size   : [32m%s[0m  [2m(%d bytes)[0m
                  Last updated : %s
                ------------------------------------------------------------
                  Per namespace:
                %s
                [32m============================================================[0m
                """.formatted(
                    banner,
                    dataDirectory.toString(),
                    stats.namespaceCount(),
                    stats.documentCount(),
                    humanBytes(stats.indexSizeBytes()),
                    stats.indexSizeBytes(),
                    lastUpdated,
                    perNamespace));
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
