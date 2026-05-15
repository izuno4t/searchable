package com.searchable.core.application.config;

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
 * Reads {@link ApplicationConfig} from a YAML source.
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

    public ApplicationConfig load(final Path file) {
        Objects.requireNonNull(file, "file must not be null");
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config from " + file, e);
        }
    }

    public ApplicationConfig load(final InputStream input) {
        Objects.requireNonNull(input, "input must not be null");
        try {
            return objectMapper.readValue(input, ApplicationConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML config", e);
        }
    }
}
