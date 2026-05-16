package io.searchable.core.infrastructure.chunking;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.parser.DocumentParser;
import io.searchable.core.domain.parser.ParsedDocument;
import io.searchable.core.domain.parser.ParsedDocument.Section;
import io.searchable.core.infrastructure.parser.MarkdownParser;
import io.searchable.core.infrastructure.parser.ParserRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SectionChunkingStrategyTest {

    private Document markdownDoc(final String id, final String title, final String content) {
        return Document.builder()
            .id(id).namespaceId("ns").title(title).content(content)
            .metadata(Map.of("format", "markdown"))
            .build();
    }

    @Test
    void markdownDocumentProducesOneChunkPerSection() {
        final String content = """
            これは導入文。

            # 第1章

            第1章の本文。

            # 第2章

            第2章の本文。

            ## 2.1 小節

            小節の本文。
            """;
        final List<Chunk> chunks = new SectionChunkingStrategy()
            .chunk(markdownDoc("doc-1", "タイトル", content));

        assertThat(chunks).extracting(c -> (String) c.metadata().get("heading"))
            .containsExactly("第1章", "第2章", "2.1 小節");
        assertThat(chunks.get(0).text()).contains("タイトル").contains("第1章");
    }

    @Test
    void fallsBackToWholeDocumentWhenNoSections() {
        final Document doc = Document.builder()
            .id("d1").namespaceId("ns").title("t").content("本文のみ")
            .metadata(Map.of("format", "plain"))
            .build();
        final List<Chunk> chunks = new SectionChunkingStrategy().chunk(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("本文のみ");
    }

    @Test
    void fallsBackWhenFormatMetadataMissing() {
        final Document doc = Document.builder()
            .id("d1").namespaceId("ns").title("t").content("# 見出し\n本文")
            .build();
        final List<Chunk> chunks = new SectionChunkingStrategy().chunk(doc);
        assertThat(chunks).hasSize(1);
    }

    @Test
    void customRegistryWithStubParser() {
        final DocumentParser stub = new DocumentParser() {
            @Override public String name() { return "stub"; }
            @Override public List<String> supportedExtensions() { return List.of(".stub"); }
            @Override public ParsedDocument parse(final String source, final String fallback) {
                return new ParsedDocument("t", source, Map.of("format", "stub"),
                    List.of(new Section(1, "A", "A本文"),
                            new Section(1, "B", "B本文")));
            }
        };
        final ParserRegistry registry = new ParserRegistry().register(stub);
        final SectionChunkingStrategy strategy = new SectionChunkingStrategy(
            registry, new WholeDocumentChunkingStrategy());

        final Document doc = Document.builder()
            .id("d1").namespaceId("ns").title("t").content("source")
            .metadata(Map.of("format", "stub"))
            .build();

        final List<Chunk> chunks = strategy.chunk(doc);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).metadata().get("heading")).isEqualTo("A");
        assertThat(chunks.get(1).metadata().get("heading")).isEqualTo("B");
    }

    @Test
    void markdownParserExposesSections() {
        final ParsedDocument doc = new MarkdownParser().parse(
            "# A\nA本文\n\n# B\nB本文", null);
        assertThat(doc.sections()).hasSize(2);
        assertThat(doc.sections()).extracting(Section::heading).containsExactly("A", "B");
    }
}
