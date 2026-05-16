package io.searchable.cli;

import io.searchable.cli.command.BackupCommand;
import io.searchable.cli.command.DeleteCommand;
import io.searchable.cli.command.IngestCommand;
import io.searchable.cli.command.ListPluginsCommand;
import io.searchable.cli.command.RebuildCommand;
import io.searchable.cli.command.RestoreCommand;
import io.searchable.cli.command.StatusCommand;
import io.searchable.cli.command.ValidateConfigCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * Entry point for the {@code searchable} command-line tool (TASK-092).
 *
 * <p>Use {@code java -jar searchable-cli.jar --help} to discover the
 * available subcommands. Every subcommand requires {@code --config} so
 * that the underlying {@link io.searchable.core.SearchableLibrary} can be
 * bootstrapped consistently (TASK-093).
 */
@Command(
    name = "searchable",
    mixinStandardHelpOptions = true,
    versionProvider = SearchableCli.VersionProvider.class,
    description = "Searchable index management CLI",
    subcommands = {
        IngestCommand.class,
        DeleteCommand.class,
        RebuildCommand.class,
        StatusCommand.class,
        BackupCommand.class,
        RestoreCommand.class,
        ListPluginsCommand.class,
        ValidateConfigCommand.class
    }
)
public final class SearchableCli implements Runnable {

    @Option(names = {"-c", "--config"}, description = "Path to the YAML application config",
        scope = CommandLine.ScopeType.INHERIT)
    public Path configPath;

    @Override
    public void run() {
        // Running the parent with no subcommand prints help, similar to git.
        CommandLine.usage(this, System.out);
    }

    public static void main(final String[] args) {
        final int exit = new CommandLine(new SearchableCli()).execute(args);
        System.exit(exit);
    }

    /** picocli version provider reading from the runtime manifest. */
    public static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            final Package pkg = SearchableCli.class.getPackage();
            final String version = pkg != null ? pkg.getImplementationVersion() : null;
            return new String[] { "searchable-cli " + (version == null ? "dev" : version) };
        }
    }
}
