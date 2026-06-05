package io.searchable.admin.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Replaces the auto-bound {@link SearchableProperties} with one rooted at
 * a freshly created temp directory and a unique in-memory H2 URL, so each
 * test class is fully isolated from previous runs and from other classes
 * sharing the same JVM.
 *
 * <p>Tests opt in with {@code @Import(SearchableTestDataConfig.class)};
 * cleanup runs in {@link #cleanup()} via {@code @PreDestroy} which Spring
 * invokes when {@code @DirtiesContext} tears the context down at the end
 * of each test class.
 */
@TestConfiguration
public class SearchableTestDataConfig {

    private final Path tempDir;

    public SearchableTestDataConfig() {
        try {
            this.tempDir = Files.createTempDirectory("searchable-admin-test-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Bean
    @Primary
    public SearchableProperties testSearchableProperties() {
        final SearchableProperties p = new SearchableProperties();
        p.setDataDirectory(tempDir);
        p.getIndex().setDirectory(tempDir.resolve("indexes"));
        p.getPersistence().setType("H2");
        p.getPersistence().setUrl("jdbc:h2:mem:" + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        p.getPersistence().setUsername("sa");
        p.getPersistence().setPassword("");
        p.normalizePaths();
        return p;
    }

    @PreDestroy
    void cleanup() throws IOException {
        if (!Files.exists(tempDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort: leftover state will accumulate under tmpdir,
                    // not under the project tree, so it's harmless if a file
                    // cannot be deleted (e.g. still locked by a slow shutdown).
                }
            });
        }
    }
}
