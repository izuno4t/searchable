package io.searchable.core.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSourceTest {

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new DocumentSource(null, "/x", null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("type");
    }

    @Test
    void rejectsNullLocation() {
        assertThatThrownBy(() -> new DocumentSource("file", null, null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("location");
    }

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new DocumentSource("", "/x", null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
        assertThatThrownBy(() -> new DocumentSource("file", " ", null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("location");
    }

    @Test
    void ofFactoryNullsOptionalFields() {
        final DocumentSource s = DocumentSource.of("file", "/x");
        assertThat(s.type()).isEqualTo("file");
        assertThat(s.location()).isEqualTo("/x");
        assertThat(s.contentHash()).isNull();
        assertThat(s.sourceUpdated()).isNull();
    }

    @Test
    void allFieldsPreserved() {
        final Instant now = Instant.now();
        final DocumentSource s = new DocumentSource("url", "https://e.x", "h", now);
        assertThat(s.contentHash()).isEqualTo("h");
        assertThat(s.sourceUpdated()).isEqualTo(now);
    }
}
