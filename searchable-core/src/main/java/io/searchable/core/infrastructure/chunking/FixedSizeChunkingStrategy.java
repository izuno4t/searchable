package io.searchable.core.infrastructure.chunking;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.chunking.ChunkingStrategy;
import io.searchable.core.domain.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits the document into fixed-character-length chunks with optional
 * overlap.
 *
 * <p>Counts Unicode code points rather than UTF-16 code units so that
 * surrogate pairs (emoji etc.) are not broken in half. The first chunk
 * is prefixed with the document title for embedding context.
 */
public final class FixedSizeChunkingStrategy implements ChunkingStrategy {

    public static final int DEFAULT_CHUNK_SIZE = 512;
    public static final int DEFAULT_OVERLAP = 64;

    private final int chunkSize;
    private final int overlap;

    public FixedSizeChunkingStrategy() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public FixedSizeChunkingStrategy(final int chunkSize, final int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must not be negative");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException(
                "overlap must be smaller than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public String name() {
        return "fixed";
    }

    @Override
    public List<Chunk> chunk(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        final String body = document.content();
        final List<int[]> windows = computeWindows(body);
        final List<Chunk> chunks = new ArrayList<>();
        final int[] codePoints = body.codePoints().toArray();

        for (int i = 0; i < windows.size(); i++) {
            final int[] window = windows.get(i);
            final String slice = new String(codePoints, window[0], window[1] - window[0]);
            final String text = i == 0 ? document.title() + "\n" + slice : slice;
            final Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("strategy", name());
            metadata.put("charStart", window[0]);
            metadata.put("charEnd", window[1]);
            chunks.add(new Chunk(
                document.id(),
                i,
                Chunk.defaultChunkId(document.id(), i),
                text,
                metadata
            ));
        }
        if (chunks.isEmpty()) {
            // Empty content: still produce a single chunk so vector indexing
            // never receives a zero-chunk document.
            chunks.add(new Chunk(
                document.id(), 0, Chunk.defaultChunkId(document.id(), 0),
                document.title(), Map.of("strategy", name())));
        }
        return chunks;
    }

    private List<int[]> computeWindows(final String body) {
        final int length = body.codePointCount(0, body.length());
        if (length == 0) {
            return List.of();
        }
        final List<int[]> windows = new ArrayList<>();
        int start = 0;
        while (start < length) {
            final int end = Math.min(start + chunkSize, length);
            windows.add(new int[]{start, end});
            if (end == length) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return windows;
    }
}
