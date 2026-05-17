package io.searchable.core.domain.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacetSpecTest {

    @Test
    void inlineFactoryUsesNameAsField() {
        final FacetSpec s = FacetSpec.inline("category");
        assertThat(s.name()).isEqualTo("category");
        assertThat(s.field()).isEqualTo("category");
        assertThat(s.mode()).isEqualTo(FacetSpec.Mode.INLINE);
    }

    @Test
    void attributeFactorySetsPath() {
        final FacetSpec s = FacetSpec.attribute("author", "attributes.author.name");
        assertThat(s.field()).isEqualTo("attributes.author.name");
        assertThat(s.mode()).isEqualTo(FacetSpec.Mode.ATTRIBUTE);
    }

    @Test
    void contentFactorySetsTagKey() {
        final FacetSpec s = FacetSpec.content("genre", "genre");
        assertThat(s.mode()).isEqualTo(FacetSpec.Mode.CONTENT);
    }

    @Test
    void rejectsNullFields() {
        assertThatThrownBy(() -> new FacetSpec(null, "f", FacetSpec.Mode.INLINE))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FacetSpec("n", null, FacetSpec.Mode.INLINE))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FacetSpec("n", "f", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new FacetSpec(" ", "f", FacetSpec.Mode.INLINE))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> new FacetSpec("n", " ", FacetSpec.Mode.INLINE))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field");
    }

    @Test
    void modeEnumExhaustive() {
        assertThat(FacetSpec.Mode.values()).containsExactly(
            FacetSpec.Mode.INLINE, FacetSpec.Mode.ATTRIBUTE, FacetSpec.Mode.CONTENT);
        assertThat(FacetSpec.Mode.valueOf("CONTENT")).isEqualTo(FacetSpec.Mode.CONTENT);
    }
}
