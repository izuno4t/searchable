package io.searchable.cli.command;

import io.searchable.cli.CliRuntime;
import io.searchable.cli.SearchableCli;
import io.searchable.core.SearchableLibrary;
import io.searchable.core.domain.document.Document;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises every CLI subcommand against a real H2-backed library.
 *
 * <p>Each test writes a YAML config under {@link #tempDir} and invokes the
 * {@link SearchableCli} root command, mirroring how end users run the tool.
 */
class CliCommandTest {

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
    void rootCommandRunMethodPrintsUsageWhenInvokedDirectly() {
        // exercises the SearchableCli#run() branch that prints usage to stdout
        new SearchableCli().run();

        assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
            .contains("Usage: searchable")
            .contains("ingest")
            .contains("status");
    }

    @Test
    void ingestSingleFileIndexesIt() throws Exception {
        final Path config = writeConfig();
        createNamespace(config, "cli-ns");
        final Path doc = tempDir.resolve("hello.txt");
        Files.writeString(doc, "Searchable は日本語検索ライブラリです。");

        final int code = run(config, "ingest", "--namespace", "cli-ns",
            "--id-prefix", "p-", doc.toString());

        assertThat(code).isZero();
        final String out = stripAnsi(stdoutBuffer.toString(StandardCharsets.UTF_8));
        assertThat(out)
            .contains("INGEST COMPLETE")
            .contains("namespace: cli-ns")
            .contains("Indexed      : 1 documents");
    }

    @Test
    void ingestDirectoryWalksAllRegularFiles() throws Exception {
        final Path config = writeConfig();
        createNamespace(config, "ns-dir");
        final Path docsDir = tempDir.resolve("docs");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("a.txt"), "alpha document");
        Files.writeString(docsDir.resolve("b.txt"), "bravo document");

        final int code = run(config, "ingest", "--namespace", "ns-dir", docsDir.toString());

        assertThat(code).isZero();
        final String out = stripAnsi(stdoutBuffer.toString(StandardCharsets.UTF_8));
        assertThat(out)
            .contains("namespace: ns-dir")
            .contains("Indexed      : 2 documents");
    }

    @Test
    void ingestSkipsFilesWithoutRegisteredParser() throws Exception {
        // Mixed directory: one parseable file and one OS metadata file
        // (.DS_Store) that no parser claims. The unsupported file must be
        // skipped with a warning, not abort the whole batch.
        final Path config = writeConfig();
        createNamespace(config, "skip-ns");
        final Path docsDir = tempDir.resolve("mixed");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("note.txt"), "alpha document");
        Files.write(docsDir.resolve(".DS_Store"), new byte[]{0, 1, 2, 3});

        final int code = run(config, "ingest", "--namespace", "skip-ns", docsDir.toString());

        assertThat(code).isZero();
        final String out = stripAnsi(stdoutBuffer.toString(StandardCharsets.UTF_8));
        assertThat(out)
            .contains("namespace: skip-ns")
            .contains("Indexed      : 1 documents")
            .contains("Skipped      : 1 files");
        assertThat(stderrBuffer.toString(StandardCharsets.UTF_8))
            .contains("WARN")
            .contains(".DS_Store");
    }

    @Test
    void ingestErrorsHelpfullyWhenNamespaceMissingAndNoFlag() throws Exception {
        // No TTY in tests + no --create-namespace flag => exit non-zero with
        // a message that points the user at the flag (no stack trace).
        final Path config = writeConfig();
        final Path doc = tempDir.resolve("hello.txt");
        Files.writeString(doc, "content");

        final int code = run(config, "ingest", "--namespace", "ghost",
            doc.toString());

        assertThat(code).isNotZero();
        assertThat(stderrBuffer.toString(StandardCharsets.UTF_8))
            .contains("Namespace 'ghost' does not exist")
            .contains("--create-namespace");
    }

    @Test
    void ingestAutoCreatesNamespaceWithFlag() throws Exception {
        // --create-namespace bypasses the prompt and creates the namespace
        // with defaults before ingesting.
        final Path config = writeConfig();
        final Path doc = tempDir.resolve("hello.txt");
        Files.writeString(doc, "content");

        final int code = run(config, "ingest", "--namespace", "auto-ns",
            "--create-namespace", doc.toString());

        assertThat(code).isZero();
        assertThat(stderrBuffer.toString(StandardCharsets.UTF_8))
            .contains("Created namespace 'auto-ns'");
        final String out = stripAnsi(stdoutBuffer.toString(StandardCharsets.UTF_8));
        assertThat(out)
            .contains("namespace: auto-ns")
            .contains("Indexed      : 1 documents");
    }

    @Test
    void ingestRejectsMissingPath() throws Exception {
        final Path config = writeConfig();
        createNamespace(config, "missing-ns");

        final int code = run(config, "ingest", "--namespace", "missing-ns",
            tempDir.resolve("does-not-exist").toString());

        // picocli wraps the exception and returns non-zero
        assertThat(code).isNotZero();
    }

    @Test
    void deleteReturnsOneWhenDocumentMissing() throws Exception {
        final Path config = writeConfig();
        createNamespace(config, "del-empty");

        final int code = run(config, "delete", "--namespace", "del-empty",
            "--id", "nope");

        assertThat(code).isEqualTo(1);
        assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
            .contains("No document with id nope in del-empty");
    }

    @Test
    void rebuildClearsNamespace() throws Exception {
        final Path config = writeConfig();
        createNamespace(config, "rebuild-ns");
        indexDocument(config, "rebuild-ns", "doc-1", "to be wiped");

        final int code = run(config, "rebuild", "--namespace", "rebuild-ns");

        assertThat(code).isZero();
        assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
            .contains("Cleared namespace rebuild-ns");
    }

    @Test
    void statusPrintsAggregateCounters() throws Exception {
        final Path config = writeConfig();
        createNamespace(config, "stat-ns");
        indexDocument(config, "stat-ns", "d1", "hello status");

        final int code = run(config, "status");

        assertThat(code).isZero();
        final String out = stdoutBuffer.toString(StandardCharsets.UTF_8);
        assertThat(out)
            .contains("Namespaces:")
            .contains("Documents :")
            .contains("Index size:")
            .contains("Last updated:");
    }

    @Test
    void listPluginsPrintsEachSpiHeader() throws Exception {
        final Path config = writeConfig();

        final int code = run(config, "list-plugins");

        assertThat(code).isZero();
        // The classpath in tests bundles at least the FilesystemDataSource
        // plugin, so we assert the SPI header and the bullet formatting
        // rather than the empty "(none)" branch (which only fires when no
        // plugin implementations are on the classpath at all).
        assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
            .contains("DataSourcePlugin:")
            .contains("  - ");
    }

    @Test
    void backupAndRestoreRoundTrip() throws Exception {
        final Path config = writeConfig();
        createNamespace(config, "backup-ns");
        indexDocument(config, "backup-ns", "bdoc", "backup content");
        final Path snapshotDir = tempDir.resolve("snapshot");

        final int backupCode = run(config, "backup", "--target", snapshotDir.toString());
        assertThat(backupCode).isZero();
        assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
            .contains("Backup taken at")
            .contains("namespaces");
        stdoutBuffer.reset();

        // Restore reads the snapshot back into a fresh persistence layer
        final Path restoreConfig = writeConfig("restore-db");
        createNamespace(restoreConfig, "backup-ns");
        final int restoreCode = run(restoreConfig, "restore",
            "--source", snapshotDir.toString());

        assertThat(restoreCode).isZero();
        assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8))
            .contains("Restored ")
            .contains("namespace(s) from");
    }

    /**
     * Drop ANSI SGR escape sequences so assertions can match the human-visible
     * text in {@link io.searchable.cli.command.IngestCommand}'s banner output.
     */
    private static String stripAnsi(final String s) {
        return s.replaceAll("\\x1b\\[[0-9;]*m", "");
    }

    private int run(final Path config, final String... commandAndArgs) {
        final String[] args = new String[commandAndArgs.length + 2];
        args[0] = "--config";
        args[1] = config.toString();
        System.arraycopy(commandAndArgs, 0, args, 2, commandAndArgs.length);
        final CommandLine cmd = new CommandLine(new SearchableCli())
            .setErr(new PrintWriter(new StringWriter()));
        return cmd.execute(args);
    }

    private void createNamespace(final Path config, final String namespaceId) {
        try (SearchableLibrary library = CliRuntime.openLibrary(config)) {
            library.namespaceService().create(
                namespaceId, namespaceId, NamespaceConfigPatch.empty());
        }
    }

    private void indexDocument(final Path config, final String namespaceId,
                                final String docId, final String content) {
        try (SearchableLibrary library = CliRuntime.openLibrary(config)) {
            library.indexService().index(Document.builder()
                .id(docId)
                .namespaceId(namespaceId)
                .title(docId)
                .content(content)
                .indexedAt(Instant.now())
                .build());
        }
    }

    private Path writeConfig() throws Exception {
        return writeConfig("cli-test-" + UUID.randomUUID());
    }

    private Path writeConfig(final String dbName) throws Exception {
        final Path workDir = tempDir.resolve("work-" + dbName);
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
