package com.searchable.core.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    @Test
    void buildsWithRequiredFields() {
        final Document doc = Document.builder()
            .id("doc-1")
            .namespaceId("ns")
            .title("タイトル")
            .content("本文")
            .build();

        assertThat(doc.id()).isEqualTo("doc-1");
        assertThat(doc.namespaceId()).isEqualTo("ns");
        assertThat(doc.metadata()).isEmpty();
        assertThat(doc.source()).isNull();
        assertThat(doc.indexedAt()).isNull();
    }

    @Test
    void rejectsMissingRequiredField() {
        assertThatThrownBy(() -> Document.builder().id("d").namespaceId("ns").title("t").build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("content");
    }

    @Test
    void metadataIsDefensivelyCopied() {
        final Map<String, Object> mutable = new HashMap<>();
        mutable.put("k", "v");
        final Document doc = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c")
            .metadata(mutable)
            .build();

        mutable.put("k2", "v2");
        assertThat(doc.metadata()).containsOnlyKeys("k");
    }

    @Test
    void toBuilderRoundTrips() {
        final Document original = Document.builder()
            .id("d").namespaceId("ns").title("t").content("c")
            .metadata(Map.of("k", "v"))
            .source(DocumentSource.of("file", "/tmp/a.txt"))
            .indexedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
        final Document copy = original.toBuilder().build();

        assertThat(copy.id()).isEqualTo(original.id());
        assertThat(copy.metadata()).isEqualTo(original.metadata());
        assertThat(copy.source()).isEqualTo(original.source());
        assertThat(copy.indexedAt()).isEqualTo(original.indexedAt());
    }
}
