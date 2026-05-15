package com.searchable.core.infrastructure.lucene;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Resolves the on-disk location of each namespace's Lucene index.
 */
public final class IndexLayout {

    private static final Pattern NAMESPACE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private final Path rootDirectory;

    public IndexLayout(final Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory must not be null");
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public Path directoryFor(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (!NAMESPACE_ID.matcher(namespaceId).matches()) {
            throw new IllegalArgumentException("Invalid namespaceId: " + namespaceId);
        }
        return rootDirectory.resolve(namespaceId);
    }
}
