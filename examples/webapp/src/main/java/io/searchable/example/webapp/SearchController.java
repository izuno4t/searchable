package io.searchable.example.webapp;

import io.searchable.core.SearchableLibrary;
import io.searchable.core.domain.search.PaginationParams;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Thymeleaf-driven search page (TASK-116) and document detail page
 * (TASK-117). Renders results as plain HTML so users can browse the
 * library-embedded webapp without any JavaScript dependency.
 */
@Controller
public class SearchController {

    private static final int PAGE_SIZE = 10;

    private final SearchableLibrary library;

    public SearchController(final SearchableLibrary library) {
        this.library = library;
    }

    @GetMapping("/")
    public String index(@RequestParam(value = "q", required = false) final String query,
                        @RequestParam(value = "page", defaultValue = "0") final int page,
                        final Model model) {
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("page", page);
        if (query != null && !query.isBlank()) {
            final SearchResult result = library.searchService().search(SearchRequest.builder()
                .query(query)
                .namespaceIds(List.of())
                .pagination(new PaginationParams(page * PAGE_SIZE, PAGE_SIZE))
                .build());
            model.addAttribute("result", result);
            model.addAttribute("totalPages",
                (int) Math.ceil(result.totalHits() / (double) PAGE_SIZE));
        }
        return "search";
    }

    @GetMapping("/documents/{namespaceId}/{documentId}")
    public String detail(@org.springframework.web.bind.annotation.PathVariable final String namespaceId,
                         @org.springframework.web.bind.annotation.PathVariable final String documentId,
                         final Model model) {
        // Search for the parent document by id; the embedded library uses
        // DocumentBrowser so reading a single doc remains cheap.
        final var page = library.documentBrowser().list(namespaceId, 0, 1000);
        final var match = page.items().stream()
            .filter(d -> d.id().equals(documentId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Document not found: " + namespaceId + "/" + documentId));
        model.addAttribute("document", match);
        return "detail";
    }
}
