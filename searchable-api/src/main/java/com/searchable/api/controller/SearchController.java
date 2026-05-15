package com.searchable.api.controller;

import com.searchable.api.dto.SearchDtos;
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
    public SearchDtos.Response search(@Valid @RequestBody final SearchDtos.Request req) {
        return SearchDtos.Response.from(service.search(req.toDomain()));
    }
}
