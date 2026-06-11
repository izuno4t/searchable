package io.searchable.core.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage for {@link DocumentMetadataRecord}'s compact-constructor
 * validation: blank id rejection, null defaults, and the 5-arg
 * back-compat constructor.
 */
class DocumentMetadataRecordValidationTest {

    @Test
    void blankNamespaceIdRejected() {
        assertThatThrownBy(() -> new DocumentMetadataRecord(
            "", "doc-1", "t", Map.of(), Instant.EPOCH))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespaceId");
    }

    @Test
    void blankDocumentIdRejected() {
        assertThatThrownBy(() -> new DocumentMetadataRecord(
            "ns", "", "t", Map.of(), Instant.EPOCH))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("documentId");
    }

    @Test
    void nullMetadataReplacedWithEmptyMap() {
        final DocumentMetadataRecord r = new DocumentMetadataRecord(
            "ns", "doc", "title", null, Instant.EPOCH);
        assertThat(r.metadata()).isEmpty();
    }

    @Test
    void metadataDefensivelyCopied() {
        final Map<String, Object> mutable = new HashMap<>();
        mutable.put("k", "v");
        final DocumentMetadataRecord r = new DocumentMetadataRecord(
            "ns", "doc", "title", mutable, Instant.EPOCH);
        mutable.put("k", "modified");
        assertThat(r.metadata().get("k")).isEqualTo("v");
    }

    @Test
    void fiveArgConstructorOmitsSource() {
        final DocumentMetadataRecord r = new DocumentMetadataRecord(
            "ns", "doc", "title", Map.of(), Instant.EPOCH);
        assertThat(r.source()).isNull();
    }
}
