package io.searchable.core.application.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reads {@link SearchableConfig} from a YAML source.
 *
 * <p>Property names follow {@code kebab-case} convention
 * (e.g. {@code data-directory}).
 */
public final class ConfigLoader {

    private final ObjectMapper objectMapper;

    public ConfigLoader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SearchableConfig load(final Path file) {
        Objects.requireNonNull(file, "file must not be null");
        try (InputStream in = Files.newInputStream(file)) {
            final SearchableConfig raw = load(in);
            // Resolve all path-typed fields against the config file's parent
            // directory (see ADR-0002). Without this, every relative path
            // would be silently resolved against the JVM CWD.
            final Path base = file.toAbsolutePath().getParent();
            return SearchableConfig.normalize(raw, base);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config from " + file, e);
        }
    }

    public SearchableConfig load(final InputStream input) {
        Objects.requireNonNull(input, "input must not be null");
        try {
            return objectMapper.readValue(input, SearchableConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML config", e);
        }
    }
}
