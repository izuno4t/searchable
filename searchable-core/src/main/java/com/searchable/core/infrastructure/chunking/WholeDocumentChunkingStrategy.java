package com.searchable.core.infrastructure.chunking;

import com.searchable.core.domain.chunking.Chunk;
import com.searchable.core.domain.chunking.ChunkingStrategy;
import com.searchable.core.domain.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default strategy that preserves backwards compatibility: produces a
 * single chunk containing the document's title and content.
 */
public final class WholeDocumentChunkingStrategy implements ChunkingStrategy {

    @Override
    public String name() {
        return "whole";
    }

    @Override
    public List<Chunk> chunk(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        final String text = document.title() + "\n" + document.content();
        return List.of(new Chunk(
            document.id(),
            0,
            Chunk.defaultChunkId(document.id(), 0),
            text,
            Map.of("strategy", name())
        ));
    }
}
