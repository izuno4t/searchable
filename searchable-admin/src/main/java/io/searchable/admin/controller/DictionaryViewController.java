package io.searchable.admin.controller;

import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import io.searchable.core.domain.dictionary.UserDictionaryRepository;
import io.searchable.admin.form.UserDictionaryForm;
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

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin UI controller for managing Kuromoji user dictionaries.
 */
@Controller
@RequestMapping("/dictionaries")
public class DictionaryViewController {

    private final UserDictionaryRepository repository;
    private final NamespaceService namespaceService;
    private final Clock clock;

    public DictionaryViewController(final UserDictionaryRepository repository,
                                    final NamespaceService namespaceService,
                                    final Clock clock) {
        this.repository = repository;
        this.namespaceService = namespaceService;
        this.clock = clock;
    }

    @GetMapping
    public String list(final Model model) {
        final List<UserDictionary> all = repository.findAll();
        UserDictionary globalDict = null;
        final Map<String, UserDictionary> byNamespace = new HashMap<>();
        for (final UserDictionary d : all) {
            switch (d.scope()) {
                case DictionaryScope.Global ignored -> globalDict = d;
                case DictionaryScope.Namespace ns -> byNamespace.put(ns.namespaceId(), d);
            }
        }
        model.addAttribute("activeNav", "dictionaries");
        model.addAttribute("pageTitle", "Dictionaries");
        model.addAttribute("globalDict", globalDict);
        model.addAttribute("dictionariesByNamespace", byNamespace);
        model.addAttribute("namespaces", namespaceService.listAll());
        return "dictionaries/list";
    }

    @GetMapping("/global/edit")
    public String editGlobal(final Model model) {
        return editForm(DictionaryScope.GLOBAL, "Global Dictionary", model);
    }

    @PostMapping("/global")
    public String saveGlobal(@Valid @ModelAttribute("form") final UserDictionaryForm form,
                             final BindingResult result,
                             final Model model,
                             final RedirectAttributes flash) {
        return save(DictionaryScope.GLOBAL, "Global Dictionary", form, result, model, flash);
    }

    @PostMapping("/global/delete")
    public String deleteGlobal(final RedirectAttributes flash) {
        return performDelete(DictionaryScope.GLOBAL, "Global", flash);
    }

    @GetMapping("/namespaces/{namespaceId}/edit")
    public String editNamespace(@PathVariable final String namespaceId, final Model model) {
        return editForm(DictionaryScope.namespace(namespaceId),
            "Namespace Dictionary: " + namespaceId, model);
    }

    @PostMapping("/namespaces/{namespaceId}")
    public String saveNamespace(@PathVariable final String namespaceId,
                                @Valid @ModelAttribute("form") final UserDictionaryForm form,
                                final BindingResult result,
                                final Model model,
                                final RedirectAttributes flash) {
        return save(DictionaryScope.namespace(namespaceId),
            "Namespace Dictionary: " + namespaceId, form, result, model, flash);
    }

    @PostMapping("/namespaces/{namespaceId}/delete")
    public String deleteNamespace(@PathVariable final String namespaceId,
                                  final RedirectAttributes flash) {
        return performDelete(DictionaryScope.namespace(namespaceId),
            "Namespace " + namespaceId, flash);
    }

    private String editForm(final DictionaryScope scope, final String pageTitle, final Model model) {
        final UserDictionary dictionary = repository.find(scope)
            .orElseGet(() -> UserDictionary.empty(scope, clock.instant()));
        prepare(model, scope, pageTitle);
        model.addAttribute("form", UserDictionaryForm.from(dictionary));
        return "dictionaries/edit";
    }

    private String save(final DictionaryScope scope, final String pageTitle,
                        final UserDictionaryForm form, final BindingResult result,
                        final Model model, final RedirectAttributes flash) {
        if (result.hasErrors()) {
            prepare(model, scope, pageTitle);
            return "dictionaries/edit";
        }
        final List<UserDictionaryEntry> entries;
        try {
            entries = form.parseEntries();
        } catch (IllegalArgumentException e) {
            result.rejectValue("entriesCsv", "parse", e.getMessage());
            prepare(model, scope, pageTitle);
            return "dictionaries/edit";
        }
        repository.save(new UserDictionary(scope, form.getName(), entries, clock.instant()));
        flash.addFlashAttribute("flashSuccess",
            "辞書 '" + scope.key() + "' を保存しました（" + entries.size() + " エントリ）");
        return "redirect:/dictionaries";
    }

    private String performDelete(final DictionaryScope scope, final String label,
                                 final RedirectAttributes flash) {
        if (repository.delete(scope)) {
            flash.addFlashAttribute("flashSuccess", label + " の辞書を削除しました");
        } else {
            flash.addFlashAttribute("flashWarning", label + " の辞書は存在しません");
        }
        return "redirect:/dictionaries";
    }

    private void prepare(final Model model, final DictionaryScope scope, final String pageTitle) {
        model.addAttribute("activeNav", "dictionaries");
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("scope", scope);
        model.addAttribute("scopeKey", scope.key());
        model.addAttribute("postAction", switch (scope) {
            case DictionaryScope.Global ignored -> "/dictionaries/global";
            case DictionaryScope.Namespace ns -> "/dictionaries/namespaces/" + ns.namespaceId();
        });
        model.addAttribute("deleteAction", switch (scope) {
            case DictionaryScope.Global ignored -> "/dictionaries/global/delete";
            case DictionaryScope.Namespace ns ->
                "/dictionaries/namespaces/" + ns.namespaceId() + "/delete";
        });
        model.addAttribute("csvHelp", Map.of(
            "format", "surface,segmentation,reading,pos",
            "example", "関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,カスタム名詞"
        ));
    }
}
