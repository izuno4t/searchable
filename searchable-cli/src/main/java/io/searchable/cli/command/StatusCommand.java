package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.IndexStatisticsService;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** {@code searchable status} -- print aggregate index statistics (TASK-097). */
@Command(name = "status", description = "Print per-namespace document counts and disk usage.")
public final class StatusCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Override
    public Integer call() {
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openReadOnlyLibrary(parent.configPath)) {
            final IndexStatisticsService.Statistics stats = library.indexStatisticsService().aggregate();
            final String dataDir = library.configuration().dataDirectory().toString();
            final String lastUpdated = stats.lastUpdated() == null
                ? "(no data)" : stats.lastUpdated().toString();

            System.out.println("""

                    [32m============================================================[0m
                      [32m[OK][0m [1mINDEX STATUS[0m  --  data: [1m%s[0m
                    [32m============================================================[0m
                      Namespaces   : [32m%d[0m
                      Documents    : [32m%d[0m
                      Index size   : [32m%s[0m  [2m(%d bytes)[0m
                      Last updated : %s
                    [32m============================================================[0m
                    """.formatted(
                        dataDir,
                        stats.namespaceCount(),
                        stats.documentCount(),
                        humanBytes(stats.indexSizeBytes()),
                        stats.indexSizeBytes(),
                        lastUpdated));
            return 0;
        }
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
