package io.searchable.core.infrastructure.chunking;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.document.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceAndParagraphChunkingTest {

    private Document doc(final String content) {
        return Document.builder()
            .id("d1").namespaceId("ns").title("title").content(content).build();
    }

    @Test
    void sentencePacksMultipleShortSentencesIntoOneChunk() {
        final List<Chunk> chunks = new SentenceChunkingStrategy(100)
            .chunk(doc("最初の文。次の文！もう一つ？"));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("最初の文")
            .contains("次の文")
            .contains("もう一つ");
    }

    @Test
    void sentenceSplitsWhenTargetSizeExceeded() {
        final String s = "あ".repeat(40) + "。";
        final List<Chunk> chunks = new SentenceChunkingStrategy(50)
            .chunk(doc(s + s + s));
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void sentenceHandlesEmptyContent() {
        final List<Chunk> chunks = new SentenceChunkingStrategy().chunk(doc(""));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("title");
    }

    @Test
    void sentenceHandlesContentWithoutTerminators() {
        final List<Chunk> chunks = new SentenceChunkingStrategy(100)
            .chunk(doc("終わりの記号がない文"));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("終わりの記号がない文");
    }

    @Test
    void paragraphSplitsByBlankLines() {
        final String content = "第一段落\n本文\n\n第二段落\n本文\n\n第三段落";
        final List<Chunk> chunks = new ParagraphChunkingStrategy().chunk(doc(content));
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).contains("title").contains("第一段落");
        assertThat(chunks.get(1).text()).contains("第二段落");
        assertThat(chunks.get(2).text()).contains("第三段落");
    }

    @Test
    void paragraphHandlesEmptyContent() {
        final List<Chunk> chunks = new ParagraphChunkingStrategy().chunk(doc(""));
        assertThat(chunks).hasSize(1);
    }
}
