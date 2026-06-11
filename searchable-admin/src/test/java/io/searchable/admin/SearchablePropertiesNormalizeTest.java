package io.searchable.admin;

import io.searchable.admin.config.SearchableProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the post-binding path-resolution branches inside
 * {@link SearchableProperties#normalizePaths()}. The Spring-driven happy path
 * is covered by {@link SearchablePropertiesTest}; this class exercises the
 * branches that depend on absolute paths and explicit overrides for
 * {@code index.directory}, {@code dictionary.directory},
 * {@code plugins.directory}, and {@code embedding.modelPath}.
 */
class SearchablePropertiesNormalizeTest {

    @TempDir Path tempDir;

    @Test
    void normalize_overridesIndexDirectoryWhenCustomRelativePath() {
        final SearchableProperties props = new SearchableProperties();
        props.setDataDirectory(tempDir);
        props.getIndex().setDirectory(Path.of("custom-indexes"));

        props.normalizePaths();

        assertThat(props.getIndex().getDirectory())
            .isEqualTo(tempDir.toAbsolutePath().resolve("custom-indexes").normalize());
    }

    @Test
    void normalize_overridesIndexDirectoryWhenAbsolute() {
        final SearchableProperties props = new SearchableProperties();
        props.setDataDirectory(tempDir);
        final Path absolute = tempDir.resolve("abs-index").toAbsolutePath();
        props.getIndex().setDirectory(absolute);

        props.normalizePaths();

        assertThat(props.getIndex().getDirectory()).isEqualTo(absolute.normalize());
    }

    @Test
    void normalize_overridesDictionaryDirectoryWhenCustomRelativePath() {
        final SearchableProperties props = new SearchableProperties();
        props.setDataDirectory(tempDir);
        props.getDictionary().setDirectory(Path.of("custom-dicts"));

        props.normalizePaths();

        assertThat(props.getDictionary().getDirectory())
            .isEqualTo(tempDir.toAbsolutePath().resolve("custom-dicts").normalize());
    }

    @Test
    void normalize_resolvesPluginsDirectoryAgainstDataDirectory() {
        final SearchableProperties props = new SearchableProperties();
        props.setDataDirectory(tempDir);
        props.getPlugins().setDirectory(Path.of("plugins"));

        props.normalizePaths();

        assertThat(props.getPlugins().getDirectory())
            .isEqualTo(tempDir.toAbsolutePath().resolve("plugins").normalize());
    }

    @Test
    void normalize_resolvesEmbeddingModelPathAgainstDataDirectory() {
        final SearchableProperties props = new SearchableProperties();
        props.setDataDirectory(tempDir);
        props.getEmbedding().setModelPath(Path.of("models/x.onnx"));

        props.normalizePaths();

        assertThat(props.getEmbedding().getModelPath())
            .isEqualTo(tempDir.toAbsolutePath().resolve("models/x.onnx").normalize());
    }

    @Test
    void aiSettersCoerceNullProviderAndModelToEmptyString() {
        final SearchableProperties.Ai ai = new SearchableProperties.Ai();
        ai.setProvider(null);
        ai.setModel(null);

        assertThat(ai.getProvider()).isEmpty();
        assertThat(ai.getModel()).isEmpty();
    }

    @Test
    void aiBeanGettersAndSettersAreReachable() {
        final SearchableProperties.Ai ai = new SearchableProperties.Ai();
        ai.setEnabled(true);
        ai.setProvider("openai");
        ai.setModel("gpt-4o-mini");
        ai.setTimeout(java.time.Duration.ofSeconds(30));
        ai.setMaxTokens(1024);
        ai.setTemperature(0.7);
        ai.setMaxContextItems(10);
        ai.setMaxContextChars(16_000);
        ai.setFallbackOnError(false);

        assertThat(ai.isEnabled()).isTrue();
        assertThat(ai.getProvider()).isEqualTo("openai");
        assertThat(ai.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(ai.getTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(ai.getMaxTokens()).isEqualTo(1024);
        assertThat(ai.getTemperature()).isEqualTo(0.7);
        assertThat(ai.getMaxContextItems()).isEqualTo(10);
        assertThat(ai.getMaxContextChars()).isEqualTo(16_000);
        assertThat(ai.isFallbackOnError()).isFalse();
    }
}
