package com.searchable.api.controller;

import com.searchable.api.dto.IndexDtos;
import com.searchable.core.application.IndexService;
import com.searchable.core.domain.document.Document;
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

    public IndexController(final IndexService service, final Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public IndexDtos.IndexedResponse index(@Valid @RequestBody final IndexDtos.IndexRequest req) {
        final Document doc = req.document().toDomain(req.namespaceId());
        service.index(doc);
        return IndexDtos.IndexedResponse.of(req.namespaceId(), doc.id(), clock.instant());
    }

    @PostMapping("/batch")
    public IndexDtos.BatchResponse batch(@Valid @RequestBody final IndexDtos.BatchRequest req) {
        final List<Document> docs = req.documents().stream()
            .map(d -> d.toDomain(req.namespaceId())).toList();
        service.indexBatch(req.namespaceId(), docs);
        final List<IndexDtos.BatchResult> results = new ArrayList<>();
        for (final Document d : docs) {
            results.add(IndexDtos.BatchResult.ok(d.id()));
        }
        return new IndexDtos.BatchResponse(docs.size(), docs.size(), 0, results);
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable final String documentId,
                                       @RequestParam("namespaceId") final String namespaceId) {
        return service.delete(namespaceId, documentId)
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
        return Map.of("namespaceId", namespaceId, "status", "REBUILT");
    }

    @GetMapping("/{namespaceId}/metadata")
    public IndexDtos.MetadataResponse metadata(@PathVariable final String namespaceId) {
        return IndexDtos.MetadataResponse.from(service.getMetadata(namespaceId));
    }
}
