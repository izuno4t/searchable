package com.searchable.api.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Response returned after successfully indexing a single document. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndexedDocumentResponse(
    String id,
    String namespaceId,
    Instant indexedAt,
    String status
) {

    public static IndexedDocumentResponse of(final String namespaceId,
                                             final String documentId,
                                             final Instant when) {
        return new IndexedDocumentResponse(documentId, namespaceId, when, "INDEXED");
    }
}
