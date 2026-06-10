package io.searchable.core.application.config;

import io.searchable.core.infrastructure.persistence.PersistenceConfig;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Root application configuration.
 *
 * <p>Loaded from a YAML file by {@link ConfigLoader}.
 *
 * <p>Path-typed fields ({@code dataDirectory}, {@code index.directory},
 * {@code plugins.directory}, and any file path embedded in
 * {@code persistence.url}) should be resolved to absolute paths before the
 * config is handed to {@code SearchableLibrary}. Use
 * {@link #normalize(SearchableConfig, Path)} for that.
 * See {@code docs/devel/adr/0002-data-directory-relative-path-resolution.md}.
 */
public record SearchableConfig(
    Path dataDirectory,
    PersistenceConfig persistence,
    IndexConfig index,
    PluginsConfig plugins,
    SearchableGlobalConfig global
) {

    public SearchableConfig {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(persistence, "persistence must not be null");
        index = index == null ? IndexConfig.defaults() : index;
        plugins = plugins == null ? PluginsConfig.classpathOnly() : plugins;
        global = global == null ? SearchableGlobalConfig.defaults() : global;
    }

    /**
     * Return a copy of {@code raw} where all path-typed fields are normalized
     * to absolute paths.
     *
     * <p>Resolution rules (see ADR-0002):
     * <ul>
     *   <li>{@code dataDirectory}: absolute as-is; relative resolved against
     *       {@code base} (typically the config file's parent directory; CWD
     *       when no config file is involved).</li>
     *   <li>{@code index.directory}: absolute as-is; relative resolved against
     *       the absolute {@code dataDirectory}. If the input was the bare
     *       {@link IndexConfig#defaults()} sentinel, replaced with
     *       {@code <dataDirectory>/indexes}.</li>
     *   <li>{@code plugins.directory}: same resolution rules as
     *       {@code index.directory}; a {@code null} directory (classpath only)
     *       is preserved.</li>
     *   <li>{@code persistence.url}: embedded H2 file paths are rewritten to
     *       absolute under the data directory. Non-file H2 modes
     *       ({@code mem:}, {@code tcp:}, {@code ssl:}, {@code zip:},
     *       {@code nio*:}, {@code memFS:}, {@code memLZF:}) and non-H2 URLs
     *       are left untouched.</li>
     * </ul>
     */
    public static SearchableConfig normalize(final SearchableConfig raw, final Path base) {
        Objects.requireNonNull(raw, "raw must not be null");
        Objects.requireNonNull(base, "base must not be null");
        final Path absoluteBase = base.toAbsolutePath().normalize();
        final Path absoluteData = resolveAgainst(raw.dataDirectory, absoluteBase);
        return new SearchableConfig(
            absoluteData,
            normalizePersistence(raw.persistence, absoluteData),
            normalizeIndex(raw.index, absoluteData),
            normalizePlugins(raw.plugins, absoluteData),
            raw.global);
    }

    private static Path resolveAgainst(final Path candidate, final Path base) {
        return candidate.isAbsolute()
            ? candidate.normalize()
            : base.resolve(candidate).normalize();
    }

    private static IndexConfig normalizeIndex(final IndexConfig idx, final Path absoluteData) {
        final Path defaultsDir = IndexConfig.defaults().directory();
        // Treat the bare defaults sentinel (Path.of("./data/indexes")) as
        // "unset" so the data-directory-relative default kicks in.
        if (idx.directory().equals(defaultsDir)) {
            return new IndexConfig(absoluteData.resolve("indexes"), idx.backend());
        }
        return new IndexConfig(resolveAgainst(idx.directory(), absoluteData), idx.backend());
    }

    private static PluginsConfig normalizePlugins(final PluginsConfig plugins, final Path absoluteData) {
        if (plugins == null || plugins.directory() == null) {
            return plugins == null ? PluginsConfig.classpathOnly() : plugins;
        }
        return new PluginsConfig(resolveAgainst(plugins.directory(), absoluteData));
    }

    private static PersistenceConfig normalizePersistence(final PersistenceConfig p, final Path absoluteData) {
        final String rewritten = normalizeH2Url(p.url(), absoluteData);
        if (rewritten.equals(p.url())) {
            return p;
        }
        return new PersistenceConfig(p.type(), rewritten, p.username(), p.password(), p.maxPoolSize());
    }

    // H2 URL forms supported (per H2 docs):
    //   jdbc:h2:[file:]<path>[;params]   -- embedded file (only this is rewritten)
    //   jdbc:h2:mem:<name>[;params]      -- in-memory, left alone
    //   jdbc:h2:tcp://host[:port]/<path> -- server, left alone
    //   jdbc:h2:ssl://...                -- server, left alone
    //   jdbc:h2:zip:<archive>!/<path>    -- zip-backed, left alone
    //   jdbc:h2:~/...                    -- H2 handles tilde expansion itself, left alone
    private static final String[] H2_NON_FILE_MODES = {
        "mem:", "tcp:", "ssl:", "zip:",
        "nio:", "nioMapped:", "nioMemFS:", "nioMemLZF:",
        "memFS:", "memLZF:"
    };

    /**
     * Rewrite an H2 JDBC URL so that:
     * <ol>
     *   <li>any embedded relative file path is resolved against
     *       {@code absoluteBase}, and</li>
     *   <li>{@code AUTO_SERVER=TRUE} is appended (unless already present)
     *       so a second process — typically the CLI running an ingest —
     *       can open the same file while an app holds it.</li>
     * </ol>
     * URLs in non-file modes ({@code mem:}, {@code tcp:}, {@code ssl:},
     * {@code zip:}, {@code nio*:}, {@code memFS:}, {@code memLZF:}) are
     * returned untouched. Non-H2 URLs are also returned unchanged.
     * Tilde-prefixed paths keep H2's native tilde handling but still get
     * {@code AUTO_SERVER=TRUE} appended.
     *
     * <p>Exposed for use by other property-binding sites (e.g. Spring Boot
     * {@code @ConfigurationProperties}) that need the same rewriting rule
     * without going through {@link #normalize(SearchableConfig, Path)}.
     */
    public static String normalizeH2Url(final String url, final Path absoluteBase) {
        final String prefix = "jdbc:h2:";
        if (url == null || !url.startsWith(prefix)) {
            return url;
        }
        String body = url.substring(prefix.length());
        for (final String mode : H2_NON_FILE_MODES) {
            if (body.startsWith(mode)) {
                return url;
            }
        }
        String filePrefix = "";
        if (body.startsWith("file:")) {
            filePrefix = "file:";
            body = body.substring("file:".length());
        }
        int paramIdx = -1;
        for (int i = 0; i < body.length(); i++) {
            final char c = body.charAt(i);
            if (c == ';' || c == '?') {
                paramIdx = i;
                break;
            }
        }
        final String pathPart = paramIdx < 0 ? body : body.substring(0, paramIdx);
        String params = paramIdx < 0 ? "" : body.substring(paramIdx);
        if (pathPart.isEmpty()) {
            return url;
        }
        final String finalPath;
        if (pathPart.startsWith("~")) {
            finalPath = pathPart;
        } else {
            final Path path = Path.of(pathPart);
            finalPath = path.isAbsolute()
                ? pathPart
                : absoluteBase.resolve(path).normalize().toString();
        }
        if (!h2ParamPresent(params, "AUTO_SERVER")) {
            params = (params.isEmpty() ? ";" : params + ";") + "AUTO_SERVER=TRUE";
        }
        return prefix + filePrefix + finalPath + params;
    }

    private static boolean h2ParamPresent(final String params, final String key) {
        if (params.isEmpty()) {
            return false;
        }
        return params.toUpperCase(Locale.ROOT)
            .contains(";" + key.toUpperCase(Locale.ROOT) + "=");
    }
}
