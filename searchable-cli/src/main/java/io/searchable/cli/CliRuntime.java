package io.searchable.cli;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.ConfigLoader;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Shared bootstrap used by every CLI subcommand (TASK-093).
 *
 * <p>Loads the {@link ApplicationConfig} from a YAML file and builds a
 * {@link SearchableLibrary}. Subcommands obtain their dependencies via
 * the returned library, then call {@link SearchableLibrary#close()} once
 * the command finishes.
 */
public final class CliRuntime {

    private CliRuntime() { }

    public static ApplicationConfig loadConfig(final Path configPath) {
        Objects.requireNonNull(configPath, "configPath must not be null");
        return new ConfigLoader().load(configPath);
    }

    public static SearchableLibrary openLibrary(final Path configPath) {
        return SearchableLibrary.fromConfig(loadConfig(configPath));
    }

    public static SearchableLibrary openReadOnlyLibrary(final Path configPath) {
        return SearchableLibrary.builder()
            .applicationConfig(loadConfig(configPath))
            .readOnly(true)
            .build();
    }
}
