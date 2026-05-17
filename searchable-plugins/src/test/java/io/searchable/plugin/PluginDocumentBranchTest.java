package io.searchable.plugin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Covers per-field branches in {@link PluginDocument}'s validator. */
class PluginDocumentBranchTest {

    @Test
    void rejectsNullInEachRequiredField() {
        assertThatThrownBy(() -> new PluginDocument(null, "t", "c", "s", "/x", null, null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> new PluginDocument("i", null, "c", "s", "/x", null, null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new PluginDocument("i", "t", null, "s", "/x", null, null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("content");
        assertThatThrownBy(() -> new PluginDocument("i", "t", "c", null, "/x", null, null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("sourceType");
        assertThatThrownBy(() -> new PluginDocument("i", "t", "c", "s", null, null, null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("sourceLocation");
    }

    @Test
    void rejectsBlankInEachRequiredField() {
        assertThatThrownBy(() -> new PluginDocument(" ", "t", "c", "s", "/x", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PluginDocument("i", " ", "c", "s", "/x", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PluginDocument("i", "t", "c", " ", "/x", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PluginDocument("i", "t", "c", "s", " ", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
