package io.searchable.example.api.controller.response;

import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexStatus;

import java.time.Instant;
import java.util.Map;

/** Response for {@code GET /api/v1/index/{namespaceId}/metadata}. */
public record IndexMetadataResponse(
    String namespaceId,
    long documentCount,
    long indexSizeBytes,
    IndexStatus status,
    Instant lastUpdated,
    Map<String, Object> statistics
) {

    public static IndexMetadataResponse from(final IndexMetadata m) {
        return new IndexMetadataResponse(m.namespaceId(), m.documentCount(),
            m.indexSizeBytes(), m.status(), m.lastUpdated(), m.statistics());
    }
}
