package com.searchable.ui.controller;

import com.searchable.core.application.NamespaceService;
import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;
import com.searchable.ui.form.NamespaceCreateForm;
import com.searchable.ui.form.NamespaceEditForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/namespaces")
public class NamespaceViewController {

    private final NamespaceService service;

    public NamespaceViewController(final NamespaceService service) {
        this.service = service;
    }

    @GetMapping
    public String list(final Model model) {
        model.addAttribute("activeNav", "namespaces");
        model.addAttribute("pageTitle", "Namespaces");
        model.addAttribute("namespaces", service.listAll());
        return "namespaces/list";
    }

    @GetMapping("/new")
    public String createForm(final Model model) {
        prepareForm(model, "New Namespace");
        model.addAttribute("form", new NamespaceCreateForm());
        return "namespaces/create";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") final NamespaceCreateForm form,
                         final BindingResult result,
                         final Model model,
                         final RedirectAttributes flash) {
        if (result.hasErrors()) {
            prepareForm(model, "New Namespace");
            return "namespaces/create";
        }
        try {
            final Namespace ns = service.create(form.getId(), form.getName(), form.toPatch());
            flash.addFlashAttribute("flashSuccess",
                "Namespace '" + ns.id() + "' を作成しました");
            return "redirect:/namespaces";
        } catch (IllegalStateException e) {
            result.reject("duplicate", e.getMessage());
            prepareForm(model, "New Namespace");
            return "namespaces/create";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable final String id, final Model model) {
        final Namespace ns = service.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + id));
        prepareForm(model, "Edit " + ns.id());
        model.addAttribute("namespace", ns);
        model.addAttribute("form", NamespaceEditForm.from(ns));
        return "namespaces/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable final String id,
                         @Valid @ModelAttribute("form") final NamespaceEditForm form,
                         final BindingResult result,
                         final Model model,
                         final RedirectAttributes flash) {
        final Namespace existing = service.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Namespace not found: " + id));
        if (result.hasErrors()) {
            prepareForm(model, "Edit " + id);
            model.addAttribute("namespace", existing);
            return "namespaces/edit";
        }
        if (!existing.name().equals(form.getName())) {
            service.rename(id, form.getName());
        }
        service.updateConfig(id, form.toPatch());
        flash.addFlashAttribute("flashSuccess", "Namespace '" + id + "' を更新しました");
        return "redirect:/namespaces";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable final String id, final RedirectAttributes flash) {
        if (service.delete(id)) {
            flash.addFlashAttribute("flashSuccess", "Namespace '" + id + "' を削除しました");
        } else {
            flash.addFlashAttribute("flashWarning",
                "Namespace '" + id + "' は既に存在しません");
        }
        return "redirect:/namespaces";
    }

    private void prepareForm(final Model model, final String pageTitle) {
        model.addAttribute("activeNav", "namespaces");
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("architectures", List.of(SearchType.values()));
        model.addAttribute("strategies", List.of(SearchStrategy.values()));
        model.addAttribute("orders", List.of(SearchOrder.values()));
    }
}
