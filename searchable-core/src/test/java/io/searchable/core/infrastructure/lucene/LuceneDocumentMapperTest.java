package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.document.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuceneDocumentMapperTest {

    private final LuceneDocumentMapper mapper = new LuceneDocumentMapper();

    @Test
    void singleArgOverloadProducesIdAndContentFields() {
        final Document doc = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c")
            .indexedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        final var lucene = mapper.toLucene(doc);
        assertThat(lucene.get(LuceneFields.PARENT_ID)).isEqualTo("d");
        assertThat(lucene.get(LuceneFields.TITLE)).isEqualTo("t");
        assertThat(lucene.get(LuceneFields.CONTENT)).contains("c");
    }

    @Test
    void twoArgOverloadAddsVector() {
        final Document doc = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c").build();
        final var lucene = mapper.toLucene(doc, new float[]{0.1f, 0.2f, 0.3f});
        assertThat(lucene.getField(LuceneFields.VECTOR)).isNotNull();
    }

    @Test
    void perChunkOverloadIncludesChunkMetadata() {
        final Document doc = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c")
            .metadata(Map.of("format", "markdown"))
            .indexedAt(Instant.now()).build();
        final Chunk chunk = new Chunk("d", 2, "d#2", "chunk text",
            Map.of("strategy", "section", "heading", "intro"));
        final var lucene = mapper.toLucene(doc, chunk, null);
        assertThat(lucene.get(LuceneFields.ID)).isEqualTo("d#2");
        assertThat(lucene.get(LuceneFields.CHUNK_METADATA_JSON))
            .contains("strategy").contains("heading");
        assertThat(lucene.getField(LuceneFields.VECTOR)).isNull();
    }

    @Test
    void rejectsNullArgs() {
        assertThatThrownBy(() -> mapper.toLucene(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mapper.toLucene(null, new float[]{0.0f}))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mapper.toLucene(null,
            new Chunk("p", 0, "id", "t", Map.of()), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deserializeMetadataReturnsEmptyForNullAndBlank() {
        assertThat(mapper.deserializeMetadata(null)).isEmpty();
        assertThat(mapper.deserializeMetadata(" ")).isEmpty();
    }

    @Test
    void deserializeMetadataParsesJson() {
        assertThat(mapper.deserializeMetadata("{\"k\":\"v\"}"))
            .containsEntry("k", "v");
    }

    @Test
    void deserializeMetadataWrapsBadJson() {
        assertThatThrownBy(() -> mapper.deserializeMetadata("{not json}"))
            .isInstanceOf(IllegalStateException.class);
    }
}
