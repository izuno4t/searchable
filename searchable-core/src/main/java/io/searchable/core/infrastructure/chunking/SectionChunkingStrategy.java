package io.searchable.core.infrastructure.chunking;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.chunking.ChunkingStrategy;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import io.searchable.core.infrastructure.parser.ParserRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Splits the document according to the structural sections produced by
 * a {@link DocumentParser}.
 *
 * <p>When the document's source format is known from
 * {@code metadata.format}, the matching parser is invoked to obtain its
 * section list. Otherwise (or when the parser produces no sections) the
 * strategy falls back to a single whole-document chunk.
 */
public final class SectionChunkingStrategy implements ChunkingStrategy {

    private final ParserRegistry registry;
    private final ChunkingStrategy fallback;

    public SectionChunkingStrategy() {
        this(ParserRegistry.defaults(), new WholeDocumentChunkingStrategy());
    }

    public SectionChunkingStrategy(final ParserRegistry registry,
                                   final ChunkingStrategy fallback) {
        this.registry = Objects.requireNonNull(registry);
        this.fallback = Objects.requireNonNull(fallback);
    }

    @Override
    public String name() {
        return "section";
    }

    @Override
    public List<Chunk> chunk(final Document document) {
        Objects.requireNonNull(document, "document must not be null");
        final List<ParsedDocument.Section> sections = resolveSections(document);
        if (sections.isEmpty()) {
            return fallback.chunk(document);
        }

        final List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            final ParsedDocument.Section section = sections.get(i);
            final String text = document.title()
                + "\n" + section.heading() + "\n" + section.content();
            final Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("strategy", name());
            meta.put("heading", section.heading());
            meta.put("level", section.level());
            chunks.add(new Chunk(document.id(), i,
                Chunk.defaultChunkId(document.id(), i), text, meta));
        }
        return chunks;
    }

    private List<ParsedDocument.Section> resolveSections(final Document document) {
        final Object format = document.metadata() != null
            ? document.metadata().get("format")
            : null;
        if (!(format instanceof String f)) {
            return List.of();
        }
        final Optional<DocumentParser> parser = registry.registeredExtensions().stream()
            .map(registry::resolveForExtension)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(p -> p.name().equalsIgnoreCase(f))
            .findFirst();
        if (parser.isEmpty()) {
            return List.of();
        }
        try {
            // Re-parse the indexed content (best-effort; binary formats unsupported).
            final ParsedDocument reparsed =
                parser.get().parse(document.content(), document.title());
            return reparsed.sections();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
