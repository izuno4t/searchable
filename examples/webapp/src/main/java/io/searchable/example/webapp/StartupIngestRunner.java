package io.searchable.example.webapp;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.infrastructure.parser.ParserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Bootstraps a default namespace and ingests every file under
 * {@code searchable.ingest.source} on application startup (TASK-115).
 *
 * <p>Disabled when {@code searchable.ingest.enabled=false}; useful when
 * the webapp is restarted against an already-populated index.
 */
@Component
public class StartupIngestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIngestRunner.class);

    private final SearchableLibrary library;
    private final boolean enabled;
    private final String namespaceId;
    private final Path source;

    public StartupIngestRunner(final SearchableLibrary library,
                               @Value("${searchable.ingest.enabled:true}") final boolean enabled,
                               @Value("${searchable.ingest.namespace:default}") final String namespaceId,
                               @Value("${searchable.ingest.source:./data/sample}") final Path source) {
        this.library = library;
        this.enabled = enabled;
        this.namespaceId = namespaceId;
        this.source = source;
    }

    @Override
    public void run(final String... args) throws Exception {
        if (!enabled) {
            log.info("startup ingest disabled");
            return;
        }
        if (library.isReadOnly()) {
            log.warn("startup ingest requested but library is read-only "
                + "(typical when the webapp shares the data directory with "
                + "the CLI ingest workflow); skipping");
            return;
        }
        if (library.namespaceService().findById(namespaceId).isEmpty()) {
            library.namespaceService().create(namespaceId, namespaceId,
                NamespaceConfigPatch.empty());
        }
        if (!Files.isDirectory(source)) {
            log.info("skip startup ingest: source {} is not a directory", source);
            return;
        }
        final ParserRegistry registry = ParserRegistry.defaults();
        try (var stream = Files.walk(source)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    final var parsed = registry.resolveForFile(path.getFileName().toString())
                        .orElse(null);
                    if (parsed == null) {
                        return;
                    }
                    final var parsedDoc = parsed.parse(Files.readString(path),
                        path.getFileName().toString());
                    final var absolute = path.toAbsolutePath();
                    library.indexService().indexIfChanged(Document.builder()
                        .id(path.getFileName().toString())
                        .namespaceId(namespaceId)
                        .title(parsedDoc.title())
                        .content(parsedDoc.content())
                        // url / contentType are reserved metadata keys
                        // (see docs/architecture.md §5.7).
                        .metadata(java.util.Map.of(
                            "url", absolute.toUri().toString(),
                            "path", absolute.toString(),
                            "contentType", parsed.contentType()))
                        .indexedAt(Instant.now())
                        .build());
                } catch (Exception e) {
                    log.warn("failed to ingest {}: {}", path, e.getMessage());
                }
            });
        }
    }
}
