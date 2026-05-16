package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.RestoreService;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** {@code searchable restore} -- restore Lucene indexes from a snapshot (TASK-098). */
@Command(name = "restore", description = "Restore Lucene indexes from a backup directory.")
public final class RestoreCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Option(names = "--source", required = true,
        description = "Backup directory previously produced by 'searchable backup'")
    Path source;

    @Override
    public Integer call() {
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openLibrary(parent.configPath)) {
            final RestoreService restore = new RestoreService(library.indexProvider(),
                new IndexLayout(library.configuration().index().directory()));
            final var restored = restore.restoreAll(source);
            System.out.printf("Restored %d namespace(s) from %s%n", restored.size(), source);
            return 0;
        }
    }
}
