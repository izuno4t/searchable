package io.searchable.core.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentPageTest {

    @Test
    void rejectsNegativeTotal() {
        assertThatThrownBy(() -> new DocumentPage(List.of(), -1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemsAreDefensivelyCopied() {
        final java.util.ArrayList<DocumentSummary> items = new java.util.ArrayList<>();
        final DocumentPage page = new DocumentPage(items, 0L);
        items.add(null); // would cause NPE later if page weren't a copy
        assertThat(page.items()).isEmpty();
    }
}
