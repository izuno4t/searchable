package io.searchable.core.application;

import java.util.List;

/** Page of documents returned by {@link DocumentBrowser}. */
public record DocumentPage(List<DocumentSummary> items, long total) {

    public DocumentPage {
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
    }
}
