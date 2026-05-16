package io.searchable.cli.command;

import io.searchable.core.application.config.ApplicationConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** {@code searchable validate-config} -- parse the YAML config without opening any resources (TASK-100). */
@Command(name = "validate-config",
    description = "Parse and validate the application config file (dry run).")
public final class ValidateConfigCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Override
    public Integer call() {
        try {
            final ApplicationConfig config = io.searchable.cli.CliRuntime.loadConfig(parent.configPath);
            System.out.println("OK: " + parent.configPath);
            System.out.println("  data-directory : " + config.dataDirectory());
            System.out.println("  persistence    : " + config.persistence().type() + " " + config.persistence().url());
            System.out.println("  index dir      : " + config.index().directory());
            System.out.println("  plugins dir    : " + config.plugins().directory());
            System.out.println("  default search : " + config.global().defaultArchitecture()
                + "/" + config.global().defaultSearchStrategy());
            return 0;
        } catch (RuntimeException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 2;
        }
    }
}
