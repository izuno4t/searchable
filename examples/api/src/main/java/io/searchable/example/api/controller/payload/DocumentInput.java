package io.searchable.example.api.controller.payload;

import io.searchable.core.domain.document.Document;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** Document payload accepted by indexing endpoints. */
public record DocumentInput(
    @NotBlank String id,
    @NotBlank String title,
    @NotBlank String content,
    Map<String, Object> metadata
) {

    public Document toDomain(final String namespaceId) {
        return Document.builder()
            .id(id).namespaceId(namespaceId)
            .title(title).content(content)
            .metadata(metadata)
            .build();
    }
}
