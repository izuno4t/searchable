package io.searchable.core.infrastructure.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserRegistryExtraTest {

    @Test
    void emptyExtensionReturnsEmpty() {
        assertThat(ParserRegistry.defaults().resolveForFile("README")).isEmpty();
        assertThat(ParserRegistry.defaults().resolveForFile("trailing.")).isEmpty();
    }

    @Test
    void resolveForExtensionAcceptsCaseInsensitive() {
        assertThat(ParserRegistry.defaults().resolveForExtension(".PDF"))
            .map(p -> p.name()).contains("pdf");
    }

    @Test
    void resolveForExtensionEmptyWhenUnknown() {
        assertThat(ParserRegistry.defaults().resolveForExtension(".csv")).isEmpty();
    }

    @Test
    void registeredExtensionsContainsBuiltIns() {
        assertThat(ParserRegistry.defaults().registeredExtensions())
            .contains(".md", ".markdown", ".adoc", ".asciidoc",
                ".html", ".htm", ".xhtml", ".pdf", ".txt", ".log", ".text");
    }

    @Test
    void rejectsNullArguments() {
        final ParserRegistry r = new ParserRegistry();
        assertThatThrownBy(() -> r.register(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> r.resolveForFile(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> r.resolveForExtension(null)).isInstanceOf(NullPointerException.class);
    }
}
