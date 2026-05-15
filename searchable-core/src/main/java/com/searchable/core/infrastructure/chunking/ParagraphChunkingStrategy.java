package com.searchable.core.infrastructure.chunking;

import com.searchable.core.domain.chunking.Chunk;
import com.searchable.core.domain.chunking.ChunkingStrategy;
import com.searchable.core.domain.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits text on blank-line boundaries (one or more consecutive line breaks).
 */
public final class ParagraphChunkingStrategy implements ChunkingStrategy {

    @Override
    public String name() {
        return "paragraph";
    }

    @Override
    public List<Chunk> chunk(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        final String[] paragraphs = document.content().split("\\R{2,}");
        final List<Chunk> chunks = new ArrayList<>();
        int ordinal = 0;
        for (final String paragraph : paragraphs) {
            final String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final String text = ordinal == 0
                ? document.title() + "\n" + trimmed
                : trimmed;
            chunks.add(new Chunk(document.id(), ordinal,
                Chunk.defaultChunkId(document.id(), ordinal),
                text,
                Map.of("strategy", name())));
            ordinal++;
        }
        if (chunks.isEmpty()) {
            chunks.add(new Chunk(document.id(), 0,
                Chunk.defaultChunkId(document.id(), 0),
                document.title(),
                Map.of("strategy", name())));
        }
        return chunks;
    }
}
