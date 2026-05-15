package com.searchable.api.controller;

import com.searchable.api.controller.request.SearchRequest;
import com.searchable.api.controller.response.SearchResponse;
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

    public SearchController(final SearchService service) {
        this.service = service;
    }

    @PostMapping
    public SearchResponse search(@Valid @RequestBody final SearchRequest req) {
        return SearchResponse.from(service.search(req.toDomain()));
    }
}
