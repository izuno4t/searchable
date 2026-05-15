package com.searchable.ui.controller;

import com.searchable.core.application.DocumentBrowser;
import com.searchable.core.application.DocumentPage;
import com.searchable.core.application.IndexService;
import com.searchable.core.application.NamespaceService;
import com.searchable.core.domain.index.IndexMetadata;
import com.searchable.core.domain.index.IndexStatus;
import com.searchable.core.domain.namespace.Namespace;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/indexes")
public class IndexViewController {

    private static final int PAGE_SIZE = 20;

    private final NamespaceService namespaceService;
    private final IndexService indexService;
    private final DocumentBrowser browser;

    public IndexViewController(final NamespaceService namespaceService,
                               final IndexService indexService,
                               final DocumentBrowser browser) {
        this.namespaceService = namespaceService;
        this.indexService = indexService;
        this.browser = browser;
    }

    @GetMapping
    public String overview(final Model model) {
        model.addAttribute("activeNav", "indexes");
        model.addAttribute("pageTitle", "Indexes");

        final List<IndexOverviewRow> rows = new ArrayList<>();
        for (final Namespace ns : namespaceService.listAll()) {
            try {
                final IndexMetadata md = indexService.getMetadata(ns.id());
                rows.add(new IndexOverviewRow(ns, md));
            } catch (NoSuchElementException e) {
                rows.add(new IndexOverviewRow(ns, null));
            }
        }
        model.addAttribute("rows", rows);
        return "indexes/list";
    }

    @GetMapping("/{namespaceId}")
    public String show(@PathVariable final String namespaceId,
                       @RequestParam(defaultValue = "0") final int page,
                       final Model model) {
        final Namespace ns = namespaceService.findById(namespaceId)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + namespaceId));
        final IndexMetadata md = indexService.getMetadata(namespaceId);
        final int safePage = Math.max(page, 0);
        final DocumentPage docs = browser.list(namespaceId, safePage * PAGE_SIZE, PAGE_SIZE);

        model.addAttribute("activeNav", "indexes");
        model.addAttribute("pageTitle", "Index: " + namespaceId);
        model.addAttribute("namespace", ns);
        model.addAttribute("metadata", md);
        model.addAttribute("documents", docs);
        model.addAttribute("page", safePage);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("totalPages", (docs.total() + PAGE_SIZE - 1) / PAGE_SIZE);
        return "indexes/show";
    }

    @PostMapping("/{namespaceId}/rebuild")
    public String rebuild(@PathVariable final String namespaceId,
                          final RedirectAttributes flash) {
        indexService.rebuild(namespaceId);
        flash.addFlashAttribute("flashSuccess",
            "Namespace '" + namespaceId + "' のインデックスを再構築しました");
        return "redirect:/indexes/" + namespaceId;
    }

    @PostMapping("/{namespaceId}/documents/{documentId}/delete")
    public String deleteDocument(@PathVariable final String namespaceId,
                                 @PathVariable final String documentId,
                                 final RedirectAttributes flash) {
        if (indexService.delete(namespaceId, documentId)) {
            flash.addFlashAttribute("flashSuccess",
                "Document '" + documentId + "' を削除しました");
        } else {
            flash.addFlashAttribute("flashWarning",
                "Document '" + documentId + "' は存在しません");
        }
        return "redirect:/indexes/" + namespaceId;
    }

    /** Row tuple for the overview screen. */
    public record IndexOverviewRow(Namespace namespace, IndexMetadata metadata) {
        public IndexStatus status() {
            return metadata == null ? IndexStatus.EMPTY : metadata.status();
        }
        public long documentCount() {
            return metadata == null ? 0L : metadata.documentCount();
        }
        public long indexSizeBytes() {
            return metadata == null ? 0L : metadata.indexSizeBytes();
        }
    }
}
