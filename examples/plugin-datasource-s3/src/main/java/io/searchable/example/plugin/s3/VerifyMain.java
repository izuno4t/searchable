package io.searchable.example.plugin.s3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import io.searchable.plugin.DataSourcePlugin;
import io.searchable.plugin.PluginContext;
import io.searchable.plugin.PluginDocument;

/**
 * Standalone verifier for the {@link S3DataSourcePlugin}.
 *
 * <p>Loads the plugin through {@link ServiceLoader} (the same path the
 * host application uses) and fetches documents from the configured
 * bucket, printing one line per object.
 *
 * <p>Inputs are read from environment variables so the entry point can
 * be invoked without recompiling:
 * <ul>
 *   <li>{@code S3_BUCKET} (required)</li>
 *   <li>{@code S3_REGION} (optional, default {@code us-east-1})</li>
 *   <li>{@code S3_PREFIX} (optional, default empty)</li>
 *   <li>{@code S3_ENDPOINT} (optional, e.g. {@code http://localhost:9000} for MinIO)</li>
 * </ul>
 */
public final class VerifyMain {

    private VerifyMain() { }

    public static void main(final String[] args) {
        final String bucket = require("S3_BUCKET");
        final Map<String, Object> config = new LinkedHashMap<>();
        config.put("bucket", bucket);
        config.put("region", System.getenv().getOrDefault("S3_REGION", "us-east-1"));
        config.put("prefix", System.getenv().getOrDefault("S3_PREFIX", ""));
        if (System.getenv("S3_ENDPOINT") != null) {
            config.put("endpointOverride", System.getenv("S3_ENDPOINT"));
        }

        final DataSourcePlugin plugin = ServiceLoader.load(DataSourcePlugin.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(p -> "s3".equals(p.name()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "S3 plugin not found via ServiceLoader (META-INF/services missing?)"));

        System.out.printf("plugin: %s%n", plugin.name());
        System.out.printf("config: %s%n", config);
        System.out.println("---");

        int count = 0;
        try (Stream<PluginDocument> stream = plugin.fetch(new PluginContext("verify", config))) {
            for (final PluginDocument doc : (Iterable<PluginDocument>) stream::iterator) {
                System.out.printf("[%d] %s%n  title=%s  bytes=%d  hash=%s%n",
                    ++count,
                    doc.sourceLocation(),
                    doc.title(),
                    doc.content().getBytes().length,
                    doc.contentHash().substring(0, 12) + "...");
            }
        }
        System.out.printf("---%nfetched %d document(s)%n", count);
    }

    private static String require(final String key) {
        final String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Required environment variable not set: " + key);
        }
        return Objects.requireNonNull(value);
    }
}
