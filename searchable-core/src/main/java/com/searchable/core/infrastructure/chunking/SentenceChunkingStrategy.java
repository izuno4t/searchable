package com.searchable.core.infrastructure.chunking;

import com.searchable.core.domain.chunking.Chunk;
import com.searchable.core.domain.chunking.ChunkingStrategy;
import com.searchable.core.domain.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits text on sentence boundaries (Japanese 。!? と Western .!?).
 *
 * <p>Multiple consecutive sentences are packed into a single chunk up
 * to {@code targetSize} characters so that very short sentences do not
 * each become a separate embedding.
 */
public final class SentenceChunkingStrategy implements ChunkingStrategy {

    private static final int DEFAULT_TARGET_SIZE = 400;

    private final int targetSize;

    public SentenceChunkingStrategy() {
        this(DEFAULT_TARGET_SIZE);
    }

    public SentenceChunkingStrategy(final int targetSize) {
        if (targetSize <= 0) {
            throw new IllegalArgumentException("targetSize must be positive");
        }
        this.targetSize = targetSize;
    }

    @Override
    public String name() {
        return "sentence";
    }

    @Override
    public List<Chunk> chunk(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        final List<String> sentences = splitSentences(document.content());
        if (sentences.isEmpty()) {
            return List.of(new Chunk(document.id(), 0,
                Chunk.defaultChunkId(document.id(), 0),
                document.title(),
                Map.of("strategy", name())));
        }

        final List<Chunk> chunks = new ArrayList<>();
        final StringBuilder buffer = new StringBuilder();
        int ordinal = 0;
        for (final String sentence : sentences) {
            if (buffer.length() > 0 && buffer.length() + sentence.length() > targetSize) {
                chunks.add(toChunk(document, ordinal++, buffer.toString()));
                buffer.setLength(0);
            }
            buffer.append(sentence);
        }
        if (buffer.length() > 0) {
            chunks.add(toChunk(document, ordinal, buffer.toString()));
        }
        return chunks;
    }

    private Chunk toChunk(final Document document, final int ordinal, final String body) {
        final String text = ordinal == 0
            ? document.title() + "\n" + body
            : body;
        final Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("strategy", name());
        meta.put("targetSize", targetSize);
        return new Chunk(document.id(), ordinal,
            Chunk.defaultChunkId(document.id(), ordinal), text, meta);
    }

    private List<String> splitSentences(final String content) {
        final List<String> sentences = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            final char c = content.charAt(i);
            current.append(c);
            if (isSentenceTerminator(c)) {
                sentences.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            final String tail = current.toString().trim();
            if (!tail.isEmpty()) {
                sentences.add(tail);
            }
        }
        return sentences;
    }

    private boolean isSentenceTerminator(final char c) {
        return c == '。' || c == '！' || c == '？'
            || c == '.' || c == '!' || c == '?';
    }
}
