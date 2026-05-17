package io.searchable.core.domain.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedDocumentTest {

    @Test
    void rejectsNullTitleOrContent() {
        assertThatThrownBy(() -> new ParsedDocument(null, "c", Map.of(), List.of()))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new ParsedDocument("t", null, Map.of(), List.of()))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("content");
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new ParsedDocument(" ", "c", Map.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullMetadataAndSectionsBecomeEmpty() {
        final ParsedDocument doc = new ParsedDocument("t", "c", null, null);
        assertThat(doc.metadata()).isEmpty();
        assertThat(doc.sections()).isEmpty();
    }

    @Test
    void backwardCompatibleConstructorHasNoSections() {
        final ParsedDocument doc = new ParsedDocument("t", "c", Map.of("k", "v"));
        assertThat(doc.metadata()).containsEntry("k", "v");
        assertThat(doc.sections()).isEmpty();
    }

    @Test
    void metadataAndSectionsAreDefensivelyCopied() {
        final Map<String, Object> meta = new HashMap<>();
        meta.put("a", 1);
        final List<ParsedDocument.Section> secs = new ArrayList<>();
        secs.add(new ParsedDocument.Section(1, "h", "body"));
        final ParsedDocument doc = new ParsedDocument("t", "c", meta, secs);

        meta.put("b", 2);
        secs.add(new ParsedDocument.Section(2, "h2", "b2"));

        assertThat(doc.metadata()).containsOnlyKeys("a");
        assertThat(doc.sections()).hasSize(1);
    }

    @Test
    void sectionRejectsNegativeLevelAndNullText() {
        assertThatThrownBy(() -> new ParsedDocument.Section(-1, "h", "c"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParsedDocument.Section(0, null, "c"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ParsedDocument.Section(0, "h", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultByteStreamParserReadsAsUtf8() throws Exception {
        final DocumentParser parser = new DocumentParser() {
            @Override public String name() { return "stub"; }
            @Override public List<String> supportedExtensions() { return List.of(".stub"); }
            @Override public ParsedDocument parse(final String source, final String fallback) {
                return new ParsedDocument(fallback, source, Map.of());
            }
        };
        try (InputStream in = new ByteArrayInputStream("こんにちは".getBytes(StandardCharsets.UTF_8))) {
            final ParsedDocument doc = parser.parse(in, "title");
            assertThat(doc.content()).isEqualTo("こんにちは");
            assertThat(doc.title()).isEqualTo("title");
        }
    }
}
