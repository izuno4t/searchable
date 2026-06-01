package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import io.searchable.core.infrastructure.parser.ParserRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@code searchable ingest} -- read a file (or a directory) and index its
 * contents into the configured namespace.
 *
 * <p>Single file ingest, batch ingest from a directory, and plugin-driven
 * ingest are all served from one command via {@code --source-type}
 * (TASK-094).
 */
@Command(name = "ingest",
    description = "Index a single file, an entire directory, or a data-source plugin batch.")
public final class IngestCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    io.searchable.cli.SearchableCli parent;

    @Option(names = "--namespace", required = true,
        description = "Target namespace id")
    String namespace;

    @Option(names = "--source-type", defaultValue = "file",
        description = "Source plugin name; 'file' uses the built-in filesystem parser")
    String sourceType;

    @Parameters(paramLabel = "PATH",
        description = "File or directory to ingest")
    Path source;

    @Option(names = "--id-prefix", description = "Prefix applied to generated document ids")
    String idPrefix = "";

    @Option(names = "--create-namespace",
        description = "Auto-create the target namespace if it does not yet exist (default in TTY: prompt)")
    Boolean autoCreateNamespace;

    @Override
    public Integer call() throws Exception {
        final long startNanos = System.nanoTime();
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openLibrary(parent.configPath)) {
            if (!ensureNamespaceExists(library)) {
                return 1;
            }
            final ParserRegistry registry = ParserRegistry.defaults();
            final List<Path> paths = collectFiles(source);
            int indexed = 0;
            int skipped = 0;
            final Map<String, Integer> byExt = new TreeMap<>();
            for (final Path path : paths) {
                final String fileName = path.getFileName().toString();
                // Unknown extensions (e.g. .DS_Store, OS metadata files) are
                // skipped with a warning rather than aborting the batch.
                final Optional<DocumentParser> parserOpt = registry.resolveForFile(fileName);
                if (parserOpt.isEmpty()) {
                    System.err.printf("WARN: No parser registered for %s -- skipping.%n", path);
                    skipped++;
                    continue;
                }
                final DocumentParser parser = parserOpt.get();
                // Use the byte-stream overload so binary-aware parsers
                // (e.g. PdfParser) work as well as text parsers. The
                // default impl on DocumentParser handles text formats by
                // decoding the stream as UTF-8.
                final ParsedDocument parsed;
                try (InputStream in = Files.newInputStream(path)) {
                    parsed = parser.parse(in, fileName);
                }
                final java.nio.file.Path absolute = path.toAbsolutePath();
                final Document doc = Document.builder()
                    .id(idPrefix + fileName)
                    .namespaceId(namespace)
                    .title(parsed.title())
                    .content(parsed.content())
                    // `url` and `contentType` are reserved metadata keys
                    // (see docs/architecture.md §5.7). Use the file URI
                    // so SearchHit.metadata.url can be opened directly
                    // from the UI, and the parser's MIME so UIs can
                    // decide how to render the original document.
                    .metadata(Map.of(
                        "url", absolute.toUri().toString(),
                        "path", absolute.toString(),
                        "contentType", parser.contentType()))
                    .indexedAt(Instant.now())
                    .build();
                library.indexService().index(doc);
                indexed++;
                byExt.merge(extensionOf(fileName), 1, Integer::sum);
            }
            printSummary(indexed, skipped, byExt, System.nanoTime() - startNanos);
            return 0;
        }
    }

    private static String extensionOf(final String fileName) {
        final int dot = fileName.lastIndexOf('.');
        return (dot < 0 || dot == fileName.length() - 1)
            ? "(no-ext)" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private void printSummary(final int indexed, final int skipped,
                              final Map<String, Integer> byExt, final long elapsedNanos) {
        final double elapsedSec = elapsedNanos / 1_000_000_000.0;
        final String breakdown = byExt.isEmpty() ? "(none)"
            : byExt.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("  "));
        final String skipOn  = skipped > 0 ? "[33m" : "";
        final String skipOff = skipped > 0 ? "[0m"  : "";

        System.out.println("""

                [32m============================================================[0m
                  [32m[OK][0m [1mINGEST COMPLETE[0m  --  namespace: [1m%s[0m
                [32m============================================================[0m
                  Source       : %s (source-type=%s)
                  Indexed      : [32m%d[0m documents
                  Skipped      : %s%d%s files (no parser)
                  Elapsed      : [2m%.2f[0m s
                ------------------------------------------------------------
                  By extension :  %s
                [32m============================================================[0m
                """.formatted(
                    namespace,
                    source.toAbsolutePath(), sourceType,
                    indexed,
                    skipOn, skipped, skipOff,
                    elapsedSec,
                    breakdown));
    }

    private List<Path> collectFiles(final Path root) throws Exception {
        if (Files.isRegularFile(root)) {
            return List.of(root);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("PATH does not exist: " + root);
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    /**
     * Ensure the target namespace exists, creating it on demand. Returns
     * {@code true} when ingestion can proceed.
     *
     * <p>Resolution order: explicit {@code --create-namespace=true/false},
     * else an interactive prompt when a TTY is attached, else a clear error
     * with the flag hint for non-interactive contexts (scripts / CI).
     */
    private boolean ensureNamespaceExists(final SearchableLibrary library) {
        final NamespaceService ns = library.namespaceService();
        if (ns.findById(namespace).isPresent()) {
            return true;
        }
        final boolean shouldCreate = decideAutoCreate();
        if (!shouldCreate) {
            System.err.printf(
                "ERROR: Namespace '%s' does not exist. Re-run with --create-namespace to auto-create.%n",
                namespace);
            return false;
        }
        ns.create(namespace, namespace, NamespaceConfigPatch.empty());
        System.err.printf("Created namespace '%s'.%n", namespace);
        return true;
    }

    private boolean decideAutoCreate() {
        if (autoCreateNamespace != null) {
            return autoCreateNamespace;
        }
        final Console console = System.console();
        if (console == null) {
            // Non-interactive: refuse rather than silently mutate state.
            return false;
        }
        final String reply = console.readLine(
            "Namespace '%s' does not exist. Create it? [Y/n]: ", namespace);
        if (reply == null) {
            return false;
        }
        final String trimmed = reply.trim().toLowerCase();
        return trimmed.isEmpty() || trimmed.equals("y") || trimmed.equals("yes");
    }
}
