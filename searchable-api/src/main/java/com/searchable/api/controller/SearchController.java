package com.searchable.api.controller;

import com.searchable.api.controller.request.SearchRequest;
import com.searchable.api.controller.response.SearchResponse;
import com.searchable.core.application.SearchPerformanceMonitor;
import com.searchable.core.application.SearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService service;
    private final SearchPerformanceMonitor monitor;

    public SearchController(final SearchService service,
                            final SearchPerformanceMonitor monitor) {
        this.service = service;
        this.monitor = monitor;
    }

    @PostMapping
    public SearchResponse search(@Valid @RequestBody final SearchRequest req) {
        final long start = System.nanoTime();
        try {
            return SearchResponse.from(service.search(req.toDomain()));
        } finally {
            monitor.record((System.nanoTime() - start) / 1_000_000);
        }
    }
}
