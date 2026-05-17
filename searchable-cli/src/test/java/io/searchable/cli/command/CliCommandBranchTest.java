package io.searchable.cli.command;

import io.searchable.cli.CliRuntime;
import io.searchable.cli.SearchableCli;
import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.IndexService;
import io.searchable.core.infrastructure.plugin.PluginLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tests that need to short-circuit {@link CliRuntime} so we
 * can drive {@link DeleteCommand} and {@link ListPluginsCommand} through
 * branches the real Lucene-backed library cannot reach (the cold-start
 * {@code isOpen} short-circuit and the bundled-plugin classpath).
 */
class CliCommandBranchTest {

    @TempDir Path tempDir;

    private PrintStream originalOut;
    private ByteArrayOutputStream stdoutBuffer;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        stdoutBuffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    private Path writeConfig() throws Exception {
        final Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
            data-directory: %s
            persistence:
              type: H2
              url: "jdbc:h2:mem:cli-mock;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
              username: sa
              password: ""
            index:
              directory: %s
            """.formatted(tempDir, tempDir.resolve("indexes")));
        return config;
    }

    private int run(final Path config, final String... commandAndArgs) {
        final String[] args = new String[commandAndArgs.length + 2];
        args[0] = "--config";
        args[1] = config.toString();
        System.arraycopy(commandAndArgs, 0, args, 2, commandAndArgs.length);
        return new CommandLine(new SearchableCli()).execute(args);
    }

    @Test
    void deleteReturnsZeroAndPrintsSuccessWhenIndexServiceConfirmsRemoval() throws Exception {
        final Path config = writeConfig();
        final SearchableLibrary library = mock(SearchableLibrary.class);
        final IndexService indexService = mock(IndexService.class);
        when(library.indexService()).thenReturn(indexService);
        when(indexService.delete("ns", "doc-1")).thenReturn(true);

        try (MockedStatic<CliRuntime> rt = mockStatic(CliRuntime.class)) {
            rt.when(() -> CliRuntime.openLibrary(any(Path.class))).thenReturn(library);

            final int code = run(config, "delete", "--namespace", "ns", "--id", "doc-1");

            assertThat(code).isZero();
            assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
                .contains("Deleted doc-1 from ns");
        }
    }

    @Test
    void listPluginsPrintsNoneWhenSpiHasNoImplementations() throws Exception {
        final Path config = writeConfig();
        final SearchableLibrary library = mock(SearchableLibrary.class);
        final PluginLoader loader = mock(PluginLoader.class);
        when(library.pluginLoader()).thenReturn(loader);
        when(loader.overview()).thenReturn(Map.of("DataSourcePlugin", List.of()));

        try (MockedStatic<CliRuntime> rt = mockStatic(CliRuntime.class)) {
            rt.when(() -> CliRuntime.openReadOnlyLibrary(any(Path.class))).thenReturn(library);

            final int code = run(config, "list-plugins");

            assertThat(code).isZero();
            assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
                .contains("DataSourcePlugin:")
                .contains("(none)");
        }
    }
}
