package com.searchable.core.domain.chunking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A piece of a {@link com.searchable.core.domain.document.Document} produced
 * by a {@link ChunkingStrategy}, suitable for independent vector embedding
 * and indexing.
 *
 * @param parentId        document id this chunk was split from
 * @param ordinal         zero-based position within the parent (0, 1, 2, ...)
 * @param chunkId         globally unique id (typically {@code parentId#ordinal})
 * @param text            actual chunk text fed to the embedding model
 * @param metadata        chunk-level metadata (e.g. heading path, char range)
 */
public record Chunk(
    String parentId,
    int ordinal,
    String chunkId,
    String text,
    Map<String, Object> metadata
) {

    public Chunk {
        Objects.requireNonNull(parentId, "parentId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (parentId.isBlank()) {
            throw new IllegalArgumentException("parentId must not be blank");
        }
        if (chunkId.isBlank()) {
            throw new IllegalArgumentException("chunkId must not be blank");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static String defaultChunkId(final String parentId, final int ordinal) {
        return parentId + "#" + ordinal;
    }
}
