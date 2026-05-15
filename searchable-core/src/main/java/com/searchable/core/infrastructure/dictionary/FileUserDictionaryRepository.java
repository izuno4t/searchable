package com.searchable.core.infrastructure.dictionary;

import com.searchable.core.domain.dictionary.DictionaryScope;
import com.searchable.core.domain.dictionary.UserDictionary;
import com.searchable.core.domain.dictionary.UserDictionaryEntry;
import com.searchable.core.domain.dictionary.UserDictionaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Stores user dictionaries as plain CSV files under a configurable root
 * directory.
 *
 * <p>Layout:
 * <pre>
 *   &lt;root&gt;/
 *     global.csv
 *     namespaces/
 *       project-a.csv
 *       project-b.csv
 * </pre>
 *
 * <p>Each CSV line uses the Kuromoji user-dictionary format
 * {@code surface,segmentation,reading,pos}; lines starting with {@code #}
 * are treated as comments.
 */
public final class FileUserDictionaryRepository implements UserDictionaryRepository {

    private static final Logger log = LoggerFactory.getLogger(FileUserDictionaryRepository.class);
    private static final String GLOBAL_FILE = "global.csv";
    private static final String NAMESPACES_DIR = "namespaces";

    private final Path root;

    public FileUserDictionaryRepository(final Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null");
    }

    @Override
    public void save(final UserDictionary dictionary) {
        Objects.requireNonNull(dictionary, "dictionary must not be null");
        final Path file = pathFor(dictionary.scope());
        try {
            Files.createDirectories(file.getParent());
            final List<String> lines = new ArrayList<>();
            lines.add("# " + dictionary.name());
            for (final UserDictionaryEntry entry : dictionary.entries()) {
                lines.add(entry.toCsv());
            }
            Files.write(file, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("saved {} entries to {}", dictionary.entries().size(), file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save dictionary " + dictionary.scope(), e);
        }
    }

    @Override
    public Optional<UserDictionary> find(final DictionaryScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        final Path file = pathFor(scope);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            final List<UserDictionaryEntry> entries = new ArrayList<>();
            String name = scope.key();
            for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    if (name.equals(scope.key())) {
                        name = trimmed.substring(1).trim().isEmpty() ? name
                            : trimmed.substring(1).trim();
                    }
                    continue;
                }
                entries.add(UserDictionaryEntry.fromCsv(trimmed));
            }
            final Instant updated = Files.getLastModifiedTime(file).toInstant();
            return Optional.of(new UserDictionary(scope, name, entries, updated));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read dictionary " + scope, e);
        }
    }

    @Override
    public List<UserDictionary> findAll() {
        final List<UserDictionary> all = new ArrayList<>();
        find(DictionaryScope.GLOBAL).ifPresent(all::add);

        final Path nsDir = root.resolve(NAMESPACES_DIR);
        if (Files.isDirectory(nsDir)) {
            try (Stream<Path> stream = Files.list(nsDir)) {
                stream
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.csv$", ""))
                    .map(DictionaryScope::namespace)
                    .map(this::find)
                    .forEach(opt -> opt.ifPresent(all::add));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to list dictionaries", e);
            }
        }
        return all;
    }

    @Override
    public boolean delete(final DictionaryScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        final Path file = pathFor(scope);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete dictionary " + scope, e);
        }
    }

    private Path pathFor(final DictionaryScope scope) {
        return switch (scope) {
            case DictionaryScope.Global ignored -> root.resolve(GLOBAL_FILE);
            case DictionaryScope.Namespace ns ->
                root.resolve(NAMESPACES_DIR).resolve(ns.namespaceId() + ".csv");
        };
    }
}
