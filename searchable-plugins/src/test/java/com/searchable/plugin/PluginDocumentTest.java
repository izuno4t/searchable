package com.searchable.plugin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginDocumentTest {

    @Test
    void buildsWithRequiredFields() {
        final PluginDocument doc = new PluginDocument(
            "id-1", "Title", "Content",
            "file", "/tmp/a.txt", null, null, null);

        assertThat(doc.id()).isEqualTo("id-1");
        assertThat(doc.metadata()).isEmpty();
    }

    @Test
    void rejectsBlankRequiredField() {
        assertThatThrownBy(() -> new PluginDocument(" ", "t", "c", "file", "/x", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metadataIsDefensivelyCopied() {
        final Map<String, Object> map = new HashMap<>();
        map.put("k", "v");
        final PluginDocument doc = new PluginDocument("id", "t", "c", "file", "/x",
            null, Instant.now(), map);
        map.put("k2", "v2");
        assertThat(doc.metadata()).containsOnlyKeys("k");
    }
}
