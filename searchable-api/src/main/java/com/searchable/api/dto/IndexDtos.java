package com.searchable.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.searchable.core.domain.document.Document;
import com.searchable.core.domain.index.IndexMetadata;
import com.searchable.core.domain.index.IndexStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Container for index-related DTOs.
 */
public final class IndexDtos {

    private IndexDtos() { }

    public record DocumentInput(@NotBlank String id, @NotBlank String title, @NotBlank String content,
                                Map<String, Object> metadata) {

        public Document toDomain(final String namespaceId) {
            return Document.builder()
                .id(id).namespaceId(namespaceId)
                .title(title).content(content)
                .metadata(metadata)
                .build();
        }
    }

    public record IndexRequest(@NotBlank String namespaceId, DocumentInput document) { }

    public record BatchRequest(@NotBlank String namespaceId, List<DocumentInput> documents) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IndexedResponse(String id, String namespaceId, Instant indexedAt, String status) {
        public static IndexedResponse of(final String namespaceId,
                                         final String documentId,
                                         final Instant when) {
            return new IndexedResponse(documentId, namespaceId, when, "INDEXED");
        }
    }

    public record BatchResponse(int total, int succeeded, int failed,
                                List<BatchResult> results) { }

    public record BatchResult(String id, String status, String error) {
        public static BatchResult ok(final String id) { return new BatchResult(id, "INDEXED", null); }
        public static BatchResult failed(final String id, final String error) {
            return new BatchResult(id, "FAILED", error);
        }
    }

    public record MetadataResponse(String namespaceId, long documentCount,
                                   long indexSizeBytes, IndexStatus status,
                                   Instant lastUpdated, Map<String, Object> statistics) {
        public static MetadataResponse from(final IndexMetadata m) {
            return new MetadataResponse(m.namespaceId(), m.documentCount(),
                m.indexSizeBytes(), m.status(), m.lastUpdated(), m.statistics());
        }
    }
}
