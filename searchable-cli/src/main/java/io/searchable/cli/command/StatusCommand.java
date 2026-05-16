package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
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
            final var stats = library.indexStatisticsService().aggregate();
            System.out.println("Namespaces: " + stats.namespaceCount());
            System.out.println("Documents : " + stats.documentCount());
            System.out.println("Index size: " + stats.indexSizeBytes() + " bytes");
            System.out.println("Last updated: " + stats.lastUpdated());
            return 0;
        }
    }
}
