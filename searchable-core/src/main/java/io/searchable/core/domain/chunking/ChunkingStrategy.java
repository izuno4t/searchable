package io.searchable.core.domain.chunking;

import io.searchable.core.domain.document.Document;

import java.util.List;

/**
 * Splits a domain {@link Document} into one or more {@link Chunk} units
 * for embedding and indexing.
 *
 * <p>Implementations are stateless and must produce deterministic output
 * for a given input. The first chunk in the returned list has ordinal 0,
 * and ordinals must be consecutive.
 */
public interface ChunkingStrategy {

    /** Short identifier used for configuration and diagnostics. */
    String name();

    /** Split a document into one or more chunks; never empty. */
    List<Chunk> chunk(Document document);
}
