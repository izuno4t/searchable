package io.searchable.admin.controller;

import java.util.List;
import java.util.Set;

import io.searchable.admin.form.AiSettingsForm;
import io.searchable.ai.AiProviderRegistry;
import io.searchable.ai.SummaryConfigProvider;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Settings page for AI summarisation (TASK-008).
 *
 * <p>Maps to {@code /settings/ai}. The page lists the providers discovered via
 * {@link java.util.ServiceLoader} (OpenAI / Anthropic / Ollama or any custom
 * implementation) and lets the operator toggle summarisation, pick a provider
 * and model, and tune timeout / context limits.
 *
 * <p>API keys are intentionally NOT exposed in the form. Each provider reads
 * its key from an environment variable (see provider Javadoc), keeping
 * secrets out of HTTP traffic and the admin database.
 */
@Controller
@RequestMapping("/settings/ai")
public class AiSettingsController {

    private final AiProviderRegistry registry;
    private final SummaryConfigProvider configProvider;

    public AiSettingsController(final AiProviderRegistry registry,
                                final SummaryConfigProvider configProvider) {
        this.registry = registry;
        this.configProvider = configProvider;
    }

    @GetMapping
    public String form(final Model model) {
        prepare(model);
        model.addAttribute("form", AiSettingsForm.from(configProvider.current()));
        return "ai-settings";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") final AiSettingsForm form,
                       final BindingResult result,
                       final Model model,
                       final RedirectAttributes flash) {
        if (result.hasErrors()) {
            prepare(model);
            return "ai-settings";
        }
        try {
            configProvider.update(form.toSummaryConfig());
        } catch (final IllegalArgumentException ex) {
            prepare(model);
            model.addAttribute("flashError", ex.getMessage());
            return "ai-settings";
        }
        flash.addFlashAttribute("flashSuccess",
            "AI 設定を更新しました。以降のリクエストに反映されます。");
        return "redirect:/settings/ai";
    }

    private void prepare(final Model model) {
        model.addAttribute("activeNav", "settings");
        model.addAttribute("pageTitle", "AI Settings");
        final Set<String> names = registry.names();
        model.addAttribute("registeredProviders", List.copyOf(names));
        model.addAttribute("hasProviders", !names.isEmpty());
    }
}
