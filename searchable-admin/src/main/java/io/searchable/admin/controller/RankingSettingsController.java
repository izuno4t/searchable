package io.searchable.admin.controller;

import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;

/**
 * Ranking settings page (TASK-108).
 *
 * <p>Lets operators tune the per-namespace {@code indexWeight} alongside
 * the existing BM25 / metaWeights overrides (which are applied per
 * request via {@code SearchOptions}; the page documents how to wire them).
 */
@Controller
@RequestMapping("/settings/ranking")
public class RankingSettingsController {

    private final NamespaceService namespaceService;

    public RankingSettingsController(final NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    @GetMapping
    public String index(final Model model) {
        model.addAttribute("activeNav", "ranking");
        model.addAttribute("pageTitle", "Ranking settings");
        model.addAttribute("namespaces", namespaceService.listAll());
        model.addAttribute("form", new RankingForm());
        return "settings/ranking";
    }

    @PostMapping
    public String save(@ModelAttribute("form") final RankingForm form,
                       final RedirectAttributes flash) {
        final Namespace existing = namespaceService.findById(form.getNamespaceId())
            .orElseThrow(() -> new NoSuchElementException(
                "Namespace not found: " + form.getNamespaceId()));
        final NamespaceConfig current = existing.config();
        final NamespaceConfigPatch patch = new NamespaceConfigPatch(
            current.architecture(),
            current.searchStrategy(),
            current.searchOrder(),
            current.embeddingConfig(),
            current.aiConfig(),
            form.getIndexWeight(),
            current.customParams());
        namespaceService.updateConfig(form.getNamespaceId(), patch);
        flash.addFlashAttribute("flashSuccess",
            "indexWeight を " + form.getIndexWeight() + " に更新しました");
        return "redirect:/settings/ranking";
    }

    /** Form bound to the ranking settings page. */
    public static class RankingForm {
        @NotBlank
        private String namespaceId;
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        private double indexWeight = 1.0;

        public String getNamespaceId() { return namespaceId; }
        public void setNamespaceId(final String v) { this.namespaceId = v; }
        public double getIndexWeight() { return indexWeight; }
        public void setIndexWeight(final double v) { this.indexWeight = v; }
    }
}
