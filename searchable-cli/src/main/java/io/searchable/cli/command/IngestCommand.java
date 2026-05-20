package io.searchable.cli.command;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import io.searchable.core.infrastructure.parser.ParserRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    @Override
    public Integer call() throws Exception {
        try (SearchableLibrary library = io.searchable.cli.CliRuntime.openLibrary(parent.configPath)) {
            final ParserRegistry registry = ParserRegistry.defaults();
            final List<Path> paths = collectFiles(source);
            int indexed = 0;
            for (final Path path : paths) {
                final String fileName = path.getFileName().toString();
                final DocumentParser parser = registry.resolveForFile(fileName)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "No parser registered for " + path));
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
                    // `url` is the reserved metadata key for the document
                    // origin (see docs/architecture.md §5.7). Use the
                    // file URI so SearchHit.metadata.url can be opened
                    // directly from the UI and SubResult.anchorUrl can
                    // append heading slugs to it.
                    .metadata(Map.of(
                        "url", absolute.toUri().toString(),
                        "path", absolute.toString()))
                    .indexedAt(Instant.now())
                    .build();
                library.indexService().index(doc);
                indexed++;
            }
            System.out.printf("Indexed %d document(s) into %s (source-type=%s).%n",
                indexed, namespace, sourceType);
            return 0;
        }
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
}
