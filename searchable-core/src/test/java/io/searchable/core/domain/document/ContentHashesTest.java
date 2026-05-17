package io.searchable.core.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashesTest {

    @Test
    void sameInputProducesSameHash() {
        assertThat(ContentHashes.hash("t", "c"))
            .isEqualTo(ContentHashes.hash("t", "c"));
    }

    @Test
    void differentTitleProducesDifferentHash() {
        assertThat(ContentHashes.hash("a", "x"))
            .isNotEqualTo(ContentHashes.hash("b", "x"));
    }

    @Test
    void differentContentProducesDifferentHash() {
        assertThat(ContentHashes.hash("t", "x"))
            .isNotEqualTo(ContentHashes.hash("t", "y"));
    }

    @Test
    void nullTitleOrContentTreatedAsEmpty() {
        // null delimiter prevents title+content collisions
        assertThat(ContentHashes.hash(null, "x"))
            .isEqualTo(ContentHashes.hash("", "x"));
        assertThat(ContentHashes.hash("t", null))
            .isEqualTo(ContentHashes.hash("t", ""));
    }

    @Test
    void hashOfDocumentDelegatesToTitleContent() {
        final Document doc = Document.builder()
            .id("d").namespaceId("ns").title("hello").content("world")
            .indexedAt(Instant.now()).build();

        assertThat(ContentHashes.hash(doc))
            .isEqualTo(ContentHashes.hash("hello", "world"));
    }

    @Test
    void resultIsLowercaseHex64Chars() {
        final String h = ContentHashes.hash("a", "b");
        assertThat(h).matches("[0-9a-f]{64}");
    }
}
