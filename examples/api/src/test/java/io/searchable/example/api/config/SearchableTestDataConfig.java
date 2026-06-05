package io.searchable.example.api.config;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Forces every test class that imports this configuration onto a freshly
 * created temp directory and a unique in-memory H2 URL. Test-specific
 * properties bound to {@link SearchableProperties} (e.g.
 * {@code searchable.dictionary.storage}, {@code searchable.embedding.dimension})
 * are preserved — only the data-directory, index-directory, and
 * persistence-URL get overridden, via a {@link BeanPostProcessor} that
 * runs after Spring finishes binding.
 *
 * <p>Tests opt in with {@code @Import(SearchableTestDataConfig.class)};
 * cleanup runs in {@link #cleanup()} when {@code @DirtiesContext} tears
 * the context down at the end of the class.
 */
@TestConfiguration
public class SearchableTestDataConfig {

    private final Path tempDir;

    public SearchableTestDataConfig() {
        try {
            this.tempDir = Files.createTempDirectory("searchable-api-test-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Bean
    public BeanPostProcessor searchablePropertiesPathOverride() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(final Object bean, final String name)
                    throws BeansException {
                if (bean instanceof SearchableProperties p) {
                    p.setDataDirectory(tempDir);
                    p.getIndex().setDirectory(tempDir.resolve("indexes"));
                    p.getPersistence().setType("H2");
                    p.getPersistence().setUrl("jdbc:h2:mem:" + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
                    p.getPersistence().setUsername("sa");
                    p.getPersistence().setPassword("");
                    p.normalizePaths();
                }
                return bean;
            }
        };
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
                    // best-effort
                }
            });
        }
    }
}
