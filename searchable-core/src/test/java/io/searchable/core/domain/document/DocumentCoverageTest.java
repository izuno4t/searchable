package io.searchable.core.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCoverageTest {

    @Test
    void builderRejectsNullRequiredFields() {
        assertThatThrownBy(() -> Document.builder()
                .namespaceId("ns").title("t").content("c").build())
            .isInstanceOf(NullPointerException.class).hasMessageContaining("id");

        assertThatThrownBy(() -> Document.builder()
                .id("d").title("t").content("c").build())
            .isInstanceOf(NullPointerException.class).hasMessageContaining("namespaceId");

        assertThatThrownBy(() -> Document.builder()
                .id("d").namespaceId("ns").content("c").build())
            .isInstanceOf(NullPointerException.class).hasMessageContaining("title");

        assertThatThrownBy(() -> Document.builder()
                .id("d").namespaceId("ns").title("t").build())
            .isInstanceOf(NullPointerException.class).hasMessageContaining("content");
    }

    @Test
    void builderRejectsBlankIdOrNamespace() {
        assertThatThrownBy(() -> Document.builder()
                .id("").namespaceId("ns").title("t").content("c").build())
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");

        assertThatThrownBy(() -> Document.builder()
                .id("d").namespaceId("").title("t").content("c").build())
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("namespaceId");
    }

    @Test
    void metadataIsDefensivelyCopied() {
        final Map<String, Object> mutable = new HashMap<>();
        mutable.put("k", "v");
        final Document doc = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c")
            .metadata(mutable).build();

        mutable.put("k", "MUT");
        mutable.put("k2", "v2");

        assertThat(doc.metadata()).containsOnlyKeys("k");
        assertThat(doc.metadata().get("k")).isEqualTo("v");
    }

    @Test
    void nullMetadataBecomesEmptyMap() {
        final Document doc = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c").build();
        assertThat(doc.metadata()).isEmpty();
        assertThat(doc.source()).isNull();
        assertThat(doc.indexedAt()).isNull();
    }

    @Test
    void toBuilderPreservesAllFields() {
        final Instant now = Instant.parse("2026-04-01T00:00:00Z");
        final DocumentSource src = DocumentSource.of("file", "/tmp/x");
        final Document original = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c")
            .metadata(Map.of("a", 1)).source(src).indexedAt(now).build();

        final Document copy = original.toBuilder().build();

        assertThat(copy.id()).isEqualTo(original.id());
        assertThat(copy.namespaceId()).isEqualTo(original.namespaceId());
        assertThat(copy.title()).isEqualTo(original.title());
        assertThat(copy.content()).isEqualTo(original.content());
        assertThat(copy.metadata()).isEqualTo(original.metadata());
        assertThat(copy.source()).isSameAs(src);
        assertThat(copy.indexedAt()).isEqualTo(now);
    }
}
