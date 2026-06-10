package io.searchable.example.webapp;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.config.SearchableConfig;
import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.application.config.IndexConfig;
import io.searchable.core.application.config.PluginsConfig;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.runtime.PidFile;
import io.searchable.core.infrastructure.runtime.SighupListener;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Spring Boot entry point for the embedded webapp example (TASK-114).
 *
 * <p>Demonstrates the "all-in-one" deployment pattern from
 * {@code docs/devel/design/architecture/overview.md} §7.2: the same JVM process owns the index,
 * runs Thymeleaf, and serves the search UI.
 *
 * <p>The webapp opens the {@link SearchableLibrary} in read-only mode and
 * listens for {@code SIGHUP} so the CLI can hot-swap the index without
 * stopping the server.
 */
@SpringBootApplication
public class SearchableWebappApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SearchableWebappApplication.class, args);
    }

    @Bean
    public SearchableLibrary searchableLibrary(
            @Value("${searchable.data-directory:./data}") final Path dataDirectory,
            @Value("${searchable.persistence.url:jdbc:h2:./data/webapp;MODE=PostgreSQL}") final String dbUrl) {
        final SearchableConfig raw = new SearchableConfig(
            dataDirectory,
            new PersistenceConfig("H2", dbUrl, "sa", ""),
            new IndexConfig(dataDirectory.resolve("indexes")),
            PluginsConfig.classpathOnly(),
            SearchableGlobalConfig.defaults());
        // Resolve relative paths against the JVM CWD because no config-file
        // anchor exists when paths come from Spring `@Value` injection.
        // See docs/devel/adr/0002-data-directory-relative-path-resolution.md.
        final SearchableConfig config = SearchableConfig.normalize(raw, Path.of("").toAbsolutePath());
        return SearchableLibrary.builder()
            .applicationConfig(config)
            .readOnly(true)
            .build();
    }

    @Bean
    public LibraryCloser libraryCloser(final SearchableLibrary library) {
        return new LibraryCloser(library);
    }

    @Bean
    public IndexHotReloadBridge indexHotReloadBridge(final SearchableLibrary library,
                                                     final IndexStatusReporter reporter) {
        return new IndexHotReloadBridge(library, reporter);
    }

    /** Ensures the library is closed on shutdown so Lucene releases its locks. */
    public static final class LibraryCloser {
        private final SearchableLibrary library;
        LibraryCloser(final SearchableLibrary library) { this.library = library; }

        @PreDestroy
        public void close() { library.close(); }
    }

    /**
     * Owns the PID file and the {@code SIGHUP} handler that triggers a
     * library refresh. Writing the PID file lets the CLI discover this
     * process and signal it after an ingest commit.
     */
    public static final class IndexHotReloadBridge implements AutoCloseable {
        private static final Logger log = LoggerFactory.getLogger(IndexHotReloadBridge.class);
        private static final String APP_NAME = "webapp";
        private final SearchableLibrary library;
        private final IndexStatusReporter reporter;
        private final PidFile pidFile;
        private final SighupListener listener;

        IndexHotReloadBridge(final SearchableLibrary library, final IndexStatusReporter reporter) {
            this.library = library;
            this.reporter = reporter;
            this.pidFile = PidFile.open(library.configuration().dataDirectory(), APP_NAME);
            this.listener = SighupListener.install(() -> {
                final int n = library.refresh();
                log.info("SIGHUP received: refreshed {} namespace(s)", n);
                reporter.reportReload();
            });
            if (!listener.isInstalled()) {
                log.warn("webapp will not auto-refresh on CLI ingest "
                    + "(SIGHUP unavailable on this platform)");
            }
        }

        @PreDestroy
        @Override
        public void close() {
            listener.uninstall();
            pidFile.close();
        }
    }
}
