package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/** {@code searchable delete} -- remove a document by id (TASK-095). */
@Command(name = "delete", description = "Delete a document from the index.")
public final class DeleteCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Option(names = "--namespace", required = true) String namespace;
    @Option(names = "--id", required = true, description = "Document id to delete") String documentId;

    @Override
    public Integer call() {
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openLibrary(parent.configPath)) {
            final boolean removed = library.indexService().delete(namespace, documentId);
            System.out.printf(removed
                ? "Deleted %s from %s.%n"
                : "No document with id %s in %s.%n", documentId, namespace);
            return removed ? 0 : 1;
        }
    }
}
