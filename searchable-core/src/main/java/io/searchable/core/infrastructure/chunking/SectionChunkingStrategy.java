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
    private final HeadingWeights weights;

    public SectionChunkingStrategy() {
        this(ParserRegistry.defaults(), new WholeDocumentChunkingStrategy(),
            HeadingWeights.defaults());
    }

    public SectionChunkingStrategy(final ParserRegistry registry,
                                   final ChunkingStrategy fallback) {
        this(registry, fallback, HeadingWeights.defaults());
    }

    public SectionChunkingStrategy(final ParserRegistry registry,
                                   final ChunkingStrategy fallback,
                                   final HeadingWeights weights) {
        this.registry = Objects.requireNonNull(registry);
        this.fallback = Objects.requireNonNull(fallback);
        this.weights = Objects.requireNonNull(weights);
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
            final double weight = weights.weightFor(section.level());
            final String boostedHeading = repeatHeading(section.heading(), weight);
            final String text = document.title()
                + "\n" + boostedHeading
                + "\n" + section.content();
            final Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("strategy", name());
            meta.put("heading", section.heading());
            meta.put("level", section.level());
            meta.put("headingWeight", weight);
            meta.put("effectiveBoost", HeadingWeights.effectiveBoost(weight));
            chunks.add(new Chunk(document.id(), i,
                Chunk.defaultChunkId(document.id(), i), text, meta));
        }
        return chunks;
    }

    /**
     * Inflate the heading text by repeating it N additional times to apply
     * the automatic heading boost (TASK-027) under the quadratic scaling
     * defined by TASK-029. The original heading remains the first
     * occurrence so analyzers and highlighters still see the untransformed
     * surface form.
     */
    private static String repeatHeading(final String heading, final double weight) {
        if (heading == null || heading.isBlank()) {
            return heading == null ? "" : heading;
        }
        final int extra = HeadingWeights.repetitionsForWeight(weight);
        if (extra <= 0) {
            return heading;
        }
        final StringBuilder sb = new StringBuilder(heading.length() * (extra + 1) + extra);
        sb.append(heading);
        for (int i = 0; i < extra; i++) {
            sb.append(' ').append(heading);
        }
        return sb.toString();
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
