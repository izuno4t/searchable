package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.BackupService;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** {@code searchable backup} -- snapshot every namespace's Lucene index (TASK-098). */
@Command(name = "backup", description = "Snapshot Lucene indexes to a destination directory.")
public final class BackupCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Option(names = "--target", required = true,
        description = "Destination directory for the snapshot")
    Path target;

    @Override
    public Integer call() {
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openLibrary(parent.configPath)) {
            final BackupService backups = new BackupService(library.indexProvider(),
                new IndexLayout(library.configuration().index().directory()));
            final var summary = backups.snapshot(target);
            System.out.printf("Backup taken at %s -> %s (%d bytes, %d namespaces)%n",
                summary.takenAt(), target, summary.totalBytes(), summary.namespaceIds().size());
            return 0;
        }
    }
}
