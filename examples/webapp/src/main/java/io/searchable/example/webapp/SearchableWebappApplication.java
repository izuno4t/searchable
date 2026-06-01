package io.searchable.example.webapp;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.application.config.ApplicationConfig;
import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.application.config.IndexConfig;
import io.searchable.core.application.config.PluginsConfig;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Spring Boot entry point for the embedded webapp example (TASK-114).
 *
 * <p>Demonstrates the "all-in-one" deployment pattern from
 * {@code docs/architecture.md} §7.2: the same JVM process owns the index,
 * runs Thymeleaf, and serves the search UI.
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
        final ApplicationConfig raw = new ApplicationConfig(
            dataDirectory,
            new PersistenceConfig("H2", dbUrl, "sa", ""),
            new IndexConfig(dataDirectory.resolve("indexes")),
            PluginsConfig.classpathOnly(),
            GlobalConfig.defaults());
        // Resolve relative paths against the JVM CWD because no config-file
        // anchor exists when paths come from Spring `@Value` injection.
        // See docs/adr/0002-data-directory-relative-path-resolution.md.
        final ApplicationConfig config = ApplicationConfig.normalize(raw, Path.of("").toAbsolutePath());
        return SearchableLibrary.fromConfig(config);
    }

    @Bean
    public LibraryCloser libraryCloser(final SearchableLibrary library) {
        return new LibraryCloser(library);
    }

    /** Ensures the library is closed on shutdown so Lucene releases its locks. */
    public static final class LibraryCloser {
        private final SearchableLibrary library;
        LibraryCloser(final SearchableLibrary library) { this.library = library; }

        @PreDestroy
        public void close() { library.close(); }
    }
}
