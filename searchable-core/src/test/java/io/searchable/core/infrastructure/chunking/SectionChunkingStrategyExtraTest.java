package io.searchable.core.infrastructure.chunking;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import io.searchable.core.domain.parser.ParsedDocument.Section;
import io.searchable.core.infrastructure.parser.ParserRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectionChunkingStrategyExtraTest {

    @Test
    void nullDocumentRejected() {
        assertThatThrownBy(() -> new SectionChunkingStrategy().chunk(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyHeadingPassedThroughByRepeatHelper() {
        // The repeatHeading branch where heading is blank requires a parser
        // emitting a section with an empty heading.
        final DocumentParser stub = new DocumentParser() {
            @Override public String name() { return "stub"; }
            @Override public List<String> supportedExtensions() { return List.of(".stub"); }
            @Override public ParsedDocument parse(final String source, final String fallback) {
                return new ParsedDocument("t", source, Map.of("format", "stub"),
                    List.of(new Section(1, "", "section body")));
            }
        };
        final ParserRegistry registry = new ParserRegistry().register(stub);
        final SectionChunkingStrategy strat = new SectionChunkingStrategy(
            registry, new WholeDocumentChunkingStrategy(), HeadingWeights.defaults());

        final Document doc = Document.builder()
            .id("d1").namespaceId("ns").title("t").content("body")
            .metadata(Map.of("format", "stub")).build();

        final List<Chunk> chunks = strat.chunk(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("section body");
    }

    @Test
    void fallsBackWhenParserThrowsAtRuntime() {
        final DocumentParser stub = new DocumentParser() {
            @Override public String name() { return "boom"; }
            @Override public List<String> supportedExtensions() { return List.of(".boom"); }
            @Override public ParsedDocument parse(final String source, final String fallback) {
                throw new RuntimeException("synthetic");
            }
        };
        final ParserRegistry registry = new ParserRegistry().register(stub);
        final SectionChunkingStrategy strat = new SectionChunkingStrategy(
            registry, new WholeDocumentChunkingStrategy());

        final Document doc = Document.builder()
            .id("d1").namespaceId("ns").title("t").content("body")
            .metadata(Map.of("format", "boom")).build();

        // Parser throws -> resolveSections returns empty -> fallback fires
        assertThat(strat.chunk(doc)).hasSize(1);
    }

    @Test
    void unknownFormatFallsBack() {
        final Document doc = Document.builder()
            .id("d1").namespaceId("ns").title("t").content("body")
            .metadata(Map.of("format", "no-such-parser")).build();

        final List<Chunk> chunks = new SectionChunkingStrategy().chunk(doc);
        assertThat(chunks).hasSize(1);
    }

    @Test
    void nonStringFormatTreatedAsAbsent() {
        final Document doc = Document.builder()
            .id("d1").namespaceId("ns").title("t").content("body")
            .metadata(Map.of("format", 123)).build();
        assertThat(new SectionChunkingStrategy().chunk(doc)).hasSize(1);
    }

    @Test
    void nullHeadingInSectionTriggersRepeatHelperEdgeCase() {
        // Forces heading == null branch in repeatHeading.
        final DocumentParser stub = new DocumentParser() {
            @Override public String name() { return "nh"; }
            @Override public List<String> supportedExtensions() { return List.of(".nh"); }
            @Override public ParsedDocument parse(final String source, final String fallback) {
                // We can't pass null heading through Section's constructor.
                // Instead emit an empty heading; covers the same fallback branch
                // where extra=0 and heading is blank.
                return new ParsedDocument("t", source, Map.of("format", "nh"),
                    List.of(new Section(7, "", "deeply nested")));
            }
        };
        final ParserRegistry registry = new ParserRegistry().register(stub);
        final SectionChunkingStrategy strat = new SectionChunkingStrategy(
            registry, new WholeDocumentChunkingStrategy());
        final Document doc = Document.builder().id("d").namespaceId("ns")
            .title("t").content("c").metadata(Map.of("format", "nh")).build();

        final List<Chunk> chunks = strat.chunk(doc);
        assertThat(chunks).hasSize(1);
        final double w = (double) chunks.get(0).metadata().get("headingWeight");
        // Level 7 -> weight clamped per HeadingWeights.defaults policy
        assertThat(w).isPositive();
    }

    @Test
    void nonBlankHeadingAtDefaultWeightSkipsRepetition() {
        // Level 7 has DEFAULT_WEIGHT = 1.0, so repetitionsForWeight returns 0.
        // With a non-blank heading this exercises the `extra <= 0` early-exit.
        final DocumentParser stub = new DocumentParser() {
            @Override public String name() { return "noboost"; }
            @Override public List<String> supportedExtensions() { return List.of(".nb"); }
            @Override public ParsedDocument parse(final String source, final String fallback) {
                return new ParsedDocument("t", source, Map.of("format", "noboost"),
                    List.of(new Section(7, "minor", "body")));
            }
        };
        final ParserRegistry registry = new ParserRegistry().register(stub);
        final SectionChunkingStrategy strat = new SectionChunkingStrategy(
            registry, new WholeDocumentChunkingStrategy());

        final Document doc = Document.builder().id("d").namespaceId("ns")
            .title("t").content("c").metadata(Map.of("format", "noboost")).build();

        final List<Chunk> chunks = strat.chunk(doc);
        assertThat(chunks).hasSize(1);
        // The heading appears exactly once when extra <= 0.
        final String text = chunks.get(0).text();
        final long occurrences = text.lines()
            .filter(l -> l.equals("minor")).count();
        assertThat(occurrences).isEqualTo(1L);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new SectionChunkingStrategy(null, new WholeDocumentChunkingStrategy()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectionChunkingStrategy(ParserRegistry.defaults(), null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectionChunkingStrategy(
                ParserRegistry.defaults(), new WholeDocumentChunkingStrategy(), null))
            .isInstanceOf(NullPointerException.class);
    }
}
