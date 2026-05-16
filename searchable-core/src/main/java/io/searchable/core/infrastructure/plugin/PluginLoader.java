package io.searchable.core.infrastructure.plugin;

import io.searchable.plugin.DataSourcePlugin;
import io.searchable.plugin.SearchablePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Discovers and instantiates plugins using {@link ServiceLoader}.
 *
 * <p>Plugins on the application classpath are always discovered. Additionally,
 * a directory of plugin JARs can be supplied; each {@code *.jar} in that
 * directory is loaded with its own {@link URLClassLoader}.
 */
public final class PluginLoader implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

    private final Path pluginsDir;
    private URLClassLoader externalClassLoader;

    /** Build a loader that only inspects the application classpath. */
    public PluginLoader() {
        this(null);
    }

    /**
     * Build a loader that inspects the application classpath plus every
     * {@code *.jar} in {@code pluginsDir} (may be {@code null} or non-existent).
     */
    public PluginLoader(final Path pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    public List<DataSourcePlugin> loadDataSourcePlugins() {
        return loadAll(DataSourcePlugin.class);
    }

    public <T extends SearchablePlugin> List<T> loadAll(final Class<T> spi) {
        Objects.requireNonNull(spi, "spi must not be null");
        final List<T> plugins = new ArrayList<>();
        plugins.addAll(loadFromClassLoader(spi, Thread.currentThread().getContextClassLoader()));

        final ClassLoader external = externalClassLoader();
        if (external != null) {
            plugins.addAll(loadFromClassLoader(spi, external));
        }
        log.info("loaded {} plugins implementing {}", plugins.size(), spi.getSimpleName());
        return plugins;
    }

    /** Return the plugin matching {@code name}, if any. */
    public <T extends SearchablePlugin> Optional<T> findByName(final Class<T> spi, final String name) {
        Objects.requireNonNull(name, "name must not be null");
        return loadAll(spi).stream().filter(p -> name.equals(p.name())).findFirst();
    }

    /** Names of all currently discoverable plugins. */
    public Map<String, List<String>> overview() {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        result.put(DataSourcePlugin.class.getSimpleName(),
            loadDataSourcePlugins().stream().map(SearchablePlugin::name).toList());
        return result;
    }

    @Override
    public void close() {
        if (externalClassLoader != null) {
            try {
                externalClassLoader.close();
            } catch (IOException e) {
                log.warn("Failed to close plugin classloader", e);
            }
            externalClassLoader = null;
        }
    }

    private synchronized ClassLoader externalClassLoader() {
        if (externalClassLoader != null) {
            return externalClassLoader;
        }
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            final List<URL> urls = new ArrayList<>();
            for (final Path jar : stream) {
                urls.add(jar.toUri().toURL());
            }
            if (urls.isEmpty()) {
                return null;
            }
            externalClassLoader = new URLClassLoader(
                "searchable-plugins",
                urls.toArray(URL[]::new),
                Thread.currentThread().getContextClassLoader());
            log.info("loaded {} plugin jar(s) from {}", urls.size(), pluginsDir);
            return externalClassLoader;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan plugin directory " + pluginsDir, e);
        }
    }

    private <T extends SearchablePlugin> List<T> loadFromClassLoader(final Class<T> spi,
                                                                     final ClassLoader cl) {
        if (cl == null) {
            return List.of();
        }
        final List<T> result = new ArrayList<>();
        ServiceLoader.load(spi, cl).forEach(result::add);
        return result;
    }
}
