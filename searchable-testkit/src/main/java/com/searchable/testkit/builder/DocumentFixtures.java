package com.searchable.testkit.builder;

import com.searchable.core.domain.document.Document;

import java.time.Instant;

/**
 * Pre-configured {@link Document.Builder} factories.
 *
 * <p>Tests should call {@link #builder(String)} or {@link #builder(String, String)}
 * and chain only the fields that matter for their assertions.
 */
public final class DocumentFixtures {

    public static final String DEFAULT_NAMESPACE = "test-ns";
    public static final Instant DEFAULT_INDEXED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private DocumentFixtures() { }

    /** Builder with given id, default namespace, placeholder title/content. */
    public static Document.Builder builder(final String id) {
        return builder(id, DEFAULT_NAMESPACE);
    }

    public static Document.Builder builder(final String id, final String namespaceId) {
        return Document.builder()
            .id(id)
            .namespaceId(namespaceId)
            .title("Test Document " + id)
            .content("Test content for document " + id)
            .indexedAt(DEFAULT_INDEXED_AT);
    }

    /** Quick one-liner document with id only. */
    public static Document document(final String id) {
        return builder(id).build();
    }
}
