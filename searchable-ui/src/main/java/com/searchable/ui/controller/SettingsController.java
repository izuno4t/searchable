package com.searchable.ui.controller;

import com.searchable.core.application.config.GlobalConfigProvider;
import com.searchable.core.domain.search.SearchOrder;
import com.searchable.core.domain.search.SearchStrategy;
import com.searchable.core.domain.search.SearchType;
import com.searchable.ui.form.GlobalConfigForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final GlobalConfigProvider provider;

    public SettingsController(final GlobalConfigProvider provider) {
        this.provider = provider;
    }

    @GetMapping
    public String form(final Model model) {
        prepare(model);
        model.addAttribute("form", GlobalConfigForm.from(provider.current()));
        return "settings";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") final GlobalConfigForm form,
                       final BindingResult result,
                       final Model model,
                       final RedirectAttributes flash) {
        if (result.hasErrors()) {
            prepare(model);
            return "settings";
        }
        provider.update(form.toGlobalConfig());
        flash.addFlashAttribute("flashSuccess",
            "グローバル設定を更新しました（新規 Namespace から適用されます）");
        return "redirect:/settings";
    }

    private void prepare(final Model model) {
        model.addAttribute("activeNav", "settings");
        model.addAttribute("pageTitle", "Settings");
        model.addAttribute("architectures", List.of(SearchType.values()));
        model.addAttribute("strategies", List.of(SearchStrategy.values()));
        model.addAttribute("orders", List.of(SearchOrder.values()));
    }
}
