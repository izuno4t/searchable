package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** {@code searchable list-plugins} -- show discovered data-source plugins (TASK-099). */
@Command(name = "list-plugins", description = "List discovered Searchable plugins.")
public final class ListPluginsCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Override
    public Integer call() {
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openReadOnlyLibrary(parent.configPath)) {
            library.pluginLoader().overview().forEach((spi, names) -> {
                System.out.println(spi + ":");
                if (names.isEmpty()) {
                    System.out.println("  (none)");
                } else {
                    names.forEach(name -> System.out.println("  - " + name));
                }
            });
            return 0;
        }
    }
}
