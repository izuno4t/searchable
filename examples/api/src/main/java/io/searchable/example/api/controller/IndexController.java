package io.searchable.example.api.controller;

import io.searchable.example.api.controller.payload.BatchIndexResult;
import io.searchable.example.api.controller.request.BatchIndexRequest;
import io.searchable.example.api.controller.request.IndexDocumentRequest;
import io.searchable.example.api.controller.response.BatchIndexResponse;
import io.searchable.example.api.controller.response.IndexMetadataResponse;
import io.searchable.example.api.controller.response.IndexedDocumentResponse;
import io.searchable.core.application.IndexService;
import io.searchable.core.domain.document.Document;
import io.searchable.core.infrastructure.runtime.PidRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/index")
public class IndexController {

    private final IndexService service;
    private final Clock clock;
    private final PidRegistry pidRegistry;

    public IndexController(final IndexService service, final Clock clock,
                           final PidRegistry pidRegistry) {
        this.service = service;
        this.clock = clock;
        this.pidRegistry = pidRegistry;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public IndexedDocumentResponse index(@Valid @RequestBody final IndexDocumentRequest req) {
        final Document doc = req.document().toDomain(req.namespaceId());
        service.index(doc);
        pidRegistry.broadcastSighup();
        return IndexedDocumentResponse.of(req.namespaceId(), doc.id(), clock.instant());
    }

    @PostMapping("/batch")
    public BatchIndexResponse batch(@Valid @RequestBody final BatchIndexRequest req) {
        final List<Document> docs = req.documents().stream()
            .map(d -> d.toDomain(req.namespaceId())).toList();
        service.indexBatch(req.namespaceId(), docs);
        pidRegistry.broadcastSighup();
        final List<BatchIndexResult> results = new ArrayList<>();
        for (final Document d : docs) {
            results.add(BatchIndexResult.ok(d.id()));
        }
        return new BatchIndexResponse(docs.size(), docs.size(), 0, results);
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable final String documentId,
                                       @RequestParam("namespaceId") final String namespaceId) {
        final boolean removed = service.delete(namespaceId, documentId);
        if (removed) {
            pidRegistry.broadcastSighup();
        }
        return removed
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    @PostMapping("/rebuild")
    public Map<String, Object> rebuild(@RequestBody final Map<String, String> body) {
        final String namespaceId = body.get("namespaceId");
        if (namespaceId == null || namespaceId.isBlank()) {
            throw new IllegalArgumentException("namespaceId is required");
        }
        service.rebuild(namespaceId);
        pidRegistry.broadcastSighup();
        return Map.of("namespaceId", namespaceId, "status", "REBUILT");
    }

    @GetMapping("/{namespaceId}/metadata")
    public IndexMetadataResponse metadata(@PathVariable final String namespaceId) {
        return IndexMetadataResponse.from(service.getMetadata(namespaceId));
    }
}
