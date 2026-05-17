package io.searchable.core.domain.chunking;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkTest {

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new Chunk(null, 0, "id", "t", Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Chunk("p", 0, null, "t", Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Chunk("p", 0, "id", null, Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankIdsOrNegativeOrdinal() {
        assertThatThrownBy(() -> new Chunk(" ", 0, "id", "t", Map.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parentId");
        assertThatThrownBy(() -> new Chunk("p", 0, " ", "t", Map.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chunkId");
        assertThatThrownBy(() -> new Chunk("p", -1, "id", "t", Map.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ordinal");
    }

    @Test
    void nullMetadataBecomesEmpty() {
        final Chunk c = new Chunk("p", 0, "id", "t", null);
        assertThat(c.metadata()).isEmpty();
    }

    @Test
    void metadataIsDefensivelyCopied() {
        final Map<String, Object> m = new HashMap<>();
        m.put("k", "v");
        final Chunk c = new Chunk("p", 0, "id", "t", m);
        m.put("k2", "v2");
        assertThat(c.metadata()).containsOnlyKeys("k");
    }

    @Test
    void defaultChunkIdFormatsAsParentHashOrdinal() {
        assertThat(Chunk.defaultChunkId("doc-1", 3)).isEqualTo("doc-1#3");
    }
}
