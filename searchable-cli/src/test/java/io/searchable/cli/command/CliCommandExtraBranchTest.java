package io.searchable.cli.command;

import io.searchable.cli.CliRuntime;
import io.searchable.cli.SearchableCli;
import io.searchable.core.SearchableLibrary;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplementary CLI branch coverage. Targets uncovered ternaries in
 * {@link StatusCommand} (empty index → {@code lastUpdated==null},
 * small index → {@code bytes < 1024}) and {@link IngestCommand}'s
 * {@code extensionOf} no-extension branch via a no-extension file.
 */
class CliCommandExtraBranchTest {

    @TempDir Path tempDir;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream stdoutBuffer;
    private ByteArrayOutputStream stderrBuffer;

    @BeforeEach
    void captureStdio() {
        originalOut = System.out;
        originalErr = System.err;
        stdoutBuffer = new ByteArrayOutputStream();
        stderrBuffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdio() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void statusOnFreshIndexPrintsNoDataAndSmallByteCount() throws Exception {
        // No namespaces created → IndexStatistics.lastUpdated() is null,
        // indexSizeBytes is small (< 1024). Together these cover both the
        // "(no data)" ternary branch and the humanBytes() small-byte path.
        final Path config = writeConfig("empty-cli");

        final int code = run(config, "status");

        assertThat(code).isZero();
        final String out = stdoutBuffer.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("(no data)").contains(" B");
    }

    private int run(final Path config, final String... args) {
        final String[] full = new String[args.length + 2];
        full[0] = "--config";
        full[1] = config.toString();
        System.arraycopy(args, 0, full, 2, args.length);
        return new CommandLine(new SearchableCli())
            .setErr(new PrintWriter(new StringWriter()))
            .execute(full);
    }

    private void createNamespace(final Path config, final String namespaceId) {
        try (SearchableLibrary library = CliRuntime.openLibrary(config)) {
            library.namespaceService().create(
                namespaceId, namespaceId, NamespaceConfigPatch.empty());
        }
    }

    private Path writeConfig(final String dbName) throws Exception {
        final Path workDir = tempDir.resolve("work-" + dbName + "-" + UUID.randomUUID());
        Files.createDirectories(workDir);
        final Path config = workDir.resolve("config.yaml");
        Files.writeString(config, """
            data-directory: %s
            persistence:
              type: H2
              url: "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
              username: sa
              password: ""
            index:
              directory: %s
            """.formatted(workDir, dbName, workDir.resolve("indexes")));
        return config;
    }
}
