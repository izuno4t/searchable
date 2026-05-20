package io.searchable.example.mcp.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Reads {@link McpCapabilitiesConfig} from a YAML source.
 *
 * <p>Property names follow {@code kebab-case} (e.g. {@code server-info}),
 * matching the convention used by the core {@code searchable.yaml} loader.
 *
 * <p>Defaults applied during load:
 * <ul>
 *   <li>{@code server-info.version} → the Maven module version read from
 *       {@code searchable-mcp.properties} (filtered at build time) when
 *       the YAML omits or blanks it.</li>
 * </ul>
 */
public final class McpCapabilitiesLoader {

    /** Build-time properties file populated by Maven resource filtering. */
    static final String BUILD_PROPERTIES = "searchable-mcp.properties";

    /** Returned when the build properties cannot be read at runtime. */
    static final String VERSION_FALLBACK = "unknown";

    private final ObjectMapper objectMapper;

    public McpCapabilitiesLoader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public McpCapabilitiesConfig load(final Path file) {
        Objects.requireNonNull(file, "file must not be null");
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read MCP capabilities from " + file, e);
        }
    }

    public McpCapabilitiesConfig load(final InputStream input) {
        Objects.requireNonNull(input, "input must not be null");
        final RawConfig raw;
        try {
            raw = objectMapper.readValue(input, RawConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse MCP capabilities YAML", e);
        }
        return toConfig(raw);
    }

    private McpCapabilitiesConfig toConfig(final RawConfig raw) {
        if (raw == null || raw.serverInfo() == null) {
            throw new IllegalStateException("server-info is required");
        }
        final RawServerInfo si = raw.serverInfo();
        final String version = (si.version() == null || si.version().isBlank())
            ? defaultVersion()
            : si.version();
        try {
            return new McpCapabilitiesConfig(
                new McpCapabilitiesConfig.ServerInfo(si.name(), version),
                raw.instructions(),
                raw.capabilities(),
                raw.tools());
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid MCP capabilities: " + e.getMessage(), e);
        }
    }

    /**
     * Default {@code server-info.version} pulled from the build-time
     * properties file. Returns {@link #VERSION_FALLBACK} if the resource
     * is missing (e.g. when classes are loaded outside the Maven build).
     */
    static String defaultVersion() {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(BUILD_PROPERTIES)) {
            if (in == null) {
                return VERSION_FALLBACK;
            }
            final Properties props = new Properties();
            props.load(in);
            final String value = props.getProperty("version");
            return (value == null || value.isBlank()) ? VERSION_FALLBACK : value;
        } catch (IOException e) {
            return VERSION_FALLBACK;
        }
    }

    /** Intermediate YAML shape that tolerates missing optional fields. */
    private record RawConfig(
        RawServerInfo serverInfo,
        String instructions,
        Map<String, Object> capabilities,
        Map<String, McpCapabilitiesConfig.ToolOverride> tools
    ) {
    }

    private record RawServerInfo(String name, String version) {
    }
}
