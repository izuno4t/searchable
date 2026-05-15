package com.searchable.api.controller;

import com.searchable.api.controller.payload.NamespaceConfigPayload;
import com.searchable.api.controller.request.NamespaceCreateRequest;
import com.searchable.api.controller.request.NamespaceUpdateRequest;
import com.searchable.api.controller.response.NamespaceListResponse;
import com.searchable.api.controller.response.NamespaceResponse;
import com.searchable.core.application.NamespaceService;
import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.namespace.NamespaceConfigPatch;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/namespaces")
public class NamespaceController {

    private final NamespaceService service;

    public NamespaceController(final NamespaceService service) {
        this.service = service;
    }

    @GetMapping
    public NamespaceListResponse list() {
        return NamespaceListResponse.from(service.listAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NamespaceResponse create(@Valid @RequestBody final NamespaceCreateRequest req) {
        final NamespaceConfigPatch patch = req.config() == null ? null : req.config().toPatch();
        final Namespace ns = service.create(req.id(), req.name(), patch);
        return NamespaceResponse.from(ns);
    }

    @GetMapping("/{id}")
    public NamespaceResponse get(@PathVariable final String id) {
        return NamespaceResponse.from(service.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + id)));
    }

    @PutMapping("/{id}")
    public NamespaceResponse update(@PathVariable final String id,
                                    @RequestBody final NamespaceUpdateRequest req) {
        Namespace ns = service.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + id));
        if (req.name() != null && !req.name().isBlank()) {
            ns = service.rename(id, req.name());
        }
        if (req.config() != null) {
            ns = service.updateConfig(id, req.config().toPatch());
        }
        return NamespaceResponse.from(ns);
    }

    @PutMapping("/{id}/config")
    public NamespaceResponse updateConfig(@PathVariable final String id,
                                          @RequestBody final NamespaceConfigPayload config) {
        return NamespaceResponse.from(service.updateConfig(id, config.toPatch()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final String id) {
        if (service.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
