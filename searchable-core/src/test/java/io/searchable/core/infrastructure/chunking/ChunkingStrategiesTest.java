package io.searchable.core.infrastructure.chunking;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.document.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingStrategiesTest {

    private Document doc(final String id, final String title, final String content) {
        return Document.builder()
            .id(id).namespaceId("ns").title(title).content(content)
            .build();
    }

    @Test
    void wholeDocumentReturnsExactlyOneChunk() {
        final List<Chunk> chunks = new WholeDocumentChunkingStrategy()
            .chunk(doc("d1", "タイトル", "本文"));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).parentId()).isEqualTo("d1");
        assertThat(chunks.get(0).ordinal()).isZero();
        assertThat(chunks.get(0).text()).contains("タイトル").contains("本文");
    }

    @Test
    void fixedSizeSplitsLongDocumentIntoMultipleChunks() {
        final String body = "あ".repeat(1500);
        final List<Chunk> chunks = new FixedSizeChunkingStrategy(500, 50)
            .chunk(doc("d1", "t", body));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).extracting(Chunk::ordinal)
            .startsWith(0)
            .doesNotHaveDuplicates();
        // First chunk includes title prefix.
        assertThat(chunks.get(0).text()).startsWith("t");
    }

    @Test
    void fixedSizeApplyOverlapBetweenChunks() {
        final String body = "0123456789".repeat(10);
        final FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy(30, 10);
        final List<Chunk> chunks = strategy.chunk(doc("d1", "t", body));

        for (int i = 1; i < chunks.size(); i++) {
            final int prevEnd = (int) chunks.get(i - 1).metadata().get("charEnd");
            final int curStart = (int) chunks.get(i).metadata().get("charStart");
            assertThat(curStart).isLessThan(prevEnd);
        }
    }

    @Test
    void fixedSizeReturnsSingleChunkForShortDocument() {
        final List<Chunk> chunks = new FixedSizeChunkingStrategy(500, 50)
            .chunk(doc("d1", "t", "短い"));
        assertThat(chunks).hasSize(1);
    }

    @Test
    void fixedSizeHandlesEmptyContent() {
        final List<Chunk> chunks = new FixedSizeChunkingStrategy(500, 50)
            .chunk(doc("d1", "title", ""));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("title");
    }

    @Test
    void fixedSizeRejectsInvalidParameters() {
        assertThatThrownBy(() -> new FixedSizeChunkingStrategy(0, 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunkingStrategy(100, -1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunkingStrategy(100, 100))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chunkIdFollowsParentHashOrdinalConvention() {
        final List<Chunk> chunks = new FixedSizeChunkingStrategy(10, 2)
            .chunk(doc("doc-1", "t", "0123456789012345"));
        assertThat(chunks.get(0).chunkId()).isEqualTo("doc-1#0");
        assertThat(chunks.get(1).chunkId()).isEqualTo("doc-1#1");
    }

    @Test
    void surrogateSafeSplit() {
        // 4-byte UTF-8 emoji should not be broken across chunks.
        final String body = "🦀".repeat(20);
        final List<Chunk> chunks = new FixedSizeChunkingStrategy(8, 2)
            .chunk(doc("d1", "t", body));
        for (final Chunk c : chunks) {
            assertThat(c.text().codePoints().anyMatch(cp -> cp == 0xFFFD))
                .as("no replacement characters")
                .isFalse();
        }
    }
}
