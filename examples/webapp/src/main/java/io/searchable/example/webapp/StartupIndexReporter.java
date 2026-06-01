package io.searchable.example.webapp;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.namespace.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Logs a one-shot summary of what index the webapp opened, fired after
 * Spring reports {@code ApplicationReadyEvent} so the numbers reflect
 * any {@link StartupIngestRunner} work that ran during boot.
 *
 * <p>Helps users diagnose data-directory mismatches: if the printed
 * counts are zero or unexpected, the webapp is pointing at a different
 * directory than the CLI used for ingest.
 */
@Component
public class StartupIndexReporter {

    private static final Logger log = LoggerFactory.getLogger(StartupIndexReporter.class);

    private final SearchableLibrary library;

    public StartupIndexReporter(final SearchableLibrary library) {
        this.library = library;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        final var stats = library.indexStatisticsService().aggregate();
        final String dataDir = library.configuration().dataDirectory().toString();
        final String lastUpdated = stats.lastUpdated() == null
            ? "(no data)" : stats.lastUpdated().toString();
        final String perNamespace = renderPerNamespace();

        // Multi-line text block; literal ANSI escapes match the CLI banner style.
        log.info("""

                [32m============================================================[0m
                  [32m[OK][0m [1mWEBAPP READY[0m  --  data: [1m%s[0m
                [32m============================================================[0m
                  Namespaces   : [32m%d[0m
                  Documents    : [32m%d[0m
                  Index size   : [32m%s[0m  [2m(%d bytes)[0m
                  Last updated : %s
                ------------------------------------------------------------
                  Per namespace:
                %s
                [32m============================================================[0m
                """.formatted(
                    dataDir,
                    stats.namespaceCount(),
                    stats.documentCount(),
                    humanBytes(stats.indexSizeBytes()),
                    stats.indexSizeBytes(),
                    lastUpdated,
                    perNamespace));
    }

    private String renderPerNamespace() {
        final List<Namespace> all = library.namespaceRepository().findAll();
        if (all.isEmpty()) {
            return "    (none)";
        }
        return all.stream().map(ns -> {
            final Optional<IndexMetadata> md =
                library.indexMetadataRepository().findByNamespaceId(ns.id());
            final long docs = md.map(IndexMetadata::documentCount).orElse(0L);
            final long bytes = md.map(IndexMetadata::indexSizeBytes).orElse(0L);
            return String.format("    %-20s :  %d docs  /  %s",
                ns.id(), docs, humanBytes(bytes));
        }).collect(Collectors.joining(System.lineSeparator()));
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
