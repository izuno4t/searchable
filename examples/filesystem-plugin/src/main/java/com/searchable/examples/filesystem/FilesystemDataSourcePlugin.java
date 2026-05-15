package com.searchable.examples.filesystem;

import com.searchable.plugin.DataSourcePlugin;
import com.searchable.plugin.PluginContext;
import com.searchable.plugin.PluginDocument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Example {@link DataSourcePlugin} that walks a directory tree and yields
 * each readable file with one of the configured extensions.
 *
 * <p>Configuration keys (read from {@link PluginContext#config()}):
 * <ul>
 *   <li>{@code directory} (required): root directory to scan</li>
 *   <li>{@code extensions} (optional): {@code List<String>} of file
 *       extensions to include (default: {@code [".txt", ".md", ".adoc"]})</li>
 * </ul>
 */
public final class FilesystemDataSourcePlugin implements DataSourcePlugin {

    private static final List<String> DEFAULT_EXTENSIONS = List.of(".txt", ".md", ".adoc");

    @Override
    public String name() {
        return "filesystem";
    }

    @Override
    public Stream<PluginDocument> fetch(final PluginContext context) {
        final Path root = resolveDirectory(context);
        final Set<String> extensions = Set.copyOf(resolveExtensions(context));

        try {
            return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> matches(p, extensions))
                .map(p -> toDocument(root, p));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory " + root, e);
        }
    }

    private static Path resolveDirectory(final PluginContext context) {
        final Object value = context.config().get("directory");
        if (value == null) {
            throw new IllegalArgumentException(
                "Missing required 'directory' config for filesystem plugin");
        }
        final Path path = Path.of(value.toString());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                "directory does not exist or is not a directory: " + path);
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private static List<String> resolveExtensions(final PluginContext context) {
        final Object raw = context.config().get("extensions");
        if (raw instanceof List<?> list && list.stream().allMatch(o -> o instanceof String)) {
            return (List<String>) list;
        }
        return DEFAULT_EXTENSIONS;
    }

    private static boolean matches(final Path path, final Set<String> extensions) {
        final String name = path.getFileName().toString().toLowerCase();
        return extensions.stream().map(String::toLowerCase).anyMatch(name::endsWith);
    }

    private static PluginDocument toDocument(final Path root, final Path file) {
        try {
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            final String relative = root.relativize(file).toString().replace('\\', '/');
            final String hash = sha256(content);
            final Instant updated = Files.getLastModifiedTime(file).toInstant();
            final String title = file.getFileName().toString();
            return new PluginDocument(
                relative,
                title,
                content,
                "file",
                file.toAbsolutePath().toString(),
                hash,
                updated,
                Map.of("relativePath", relative)
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    private static String sha256(final String input) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
