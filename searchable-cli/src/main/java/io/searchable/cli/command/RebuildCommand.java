package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/** {@code searchable rebuild} -- truncate the namespace index (TASK-096). */
@Command(name = "rebuild", description = "Clear all documents from a namespace so it can be re-ingested.")
public final class RebuildCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Option(names = "--namespace", required = true) String namespace;

    @Override
    public Integer call() {
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openLibrary(parent.configPath)) {
            library.indexService().rebuild(namespace);
            System.out.printf("Cleared namespace %s; ready for re-ingest.%n", namespace);
            return 0;
        }
    }
}
