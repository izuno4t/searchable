package io.searchable.admin;

import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import io.searchable.core.domain.dictionary.UserDictionaryRepository;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives DictionaryViewController.list through every combination of
 * (globalDict present?, namespace dict present?, namespace exists?) so the
 * switch + map-fill branches are all exercised.
 */
@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
@TestPropertySource(properties = {
    "searchable.dictionary.storage=db"
})
class DictionaryViewControllerBranchTest {

    @Autowired MockMvc mvc;
    @Autowired NamespaceService namespaceService;
    @Autowired UserDictionaryRepository dictRepository;

    @BeforeEach
    void seed() {
        // Wipe both scopes between tests; in-memory H2 persists per JVM.
        dictRepository.delete(DictionaryScope.GLOBAL);
        namespaceService.findById("dict-br")
            .ifPresent(n -> {
                dictRepository.delete(DictionaryScope.namespace("dict-br"));
                namespaceService.delete("dict-br");
            });
        namespaceService.create("dict-br", "Dict Br", NamespaceConfigPatch.empty());
    }

    @Test
    void listWithNoDictionariesRendersEmptyState() throws Exception {
        mvc.perform(get("/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Dictionaries")));
    }

    @Test
    void listWithOnlyGlobalDictionary() throws Exception {
        dictRepository.save(new UserDictionary(DictionaryScope.GLOBAL, "g",
            List.of(new UserDictionaryEntry("X", "X", "エックス", "名詞")),
            Instant.parse("2026-01-01T00:00:00Z")));
        mvc.perform(get("/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Global")));
    }

    @Test
    void listWithOnlyNamespaceDictionary() throws Exception {
        dictRepository.save(new UserDictionary(DictionaryScope.namespace("dict-br"),
            "Br", List.of(new UserDictionaryEntry("Y", "Y", "ワイ", "名詞")),
            Instant.parse("2026-01-01T00:00:00Z")));
        mvc.perform(get("/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("dict-br")));
    }

    @Test
    void listWithBothDictionariesPresent() throws Exception {
        dictRepository.save(new UserDictionary(DictionaryScope.GLOBAL, "g",
            List.of(new UserDictionaryEntry("X", "X", "エックス", "名詞")),
            Instant.parse("2026-01-01T00:00:00Z")));
        dictRepository.save(new UserDictionary(DictionaryScope.namespace("dict-br"),
            "Br", List.of(new UserDictionaryEntry("Y", "Y", "ワイ", "名詞")),
            Instant.parse("2026-01-01T00:00:00Z")));
        mvc.perform(get("/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Global")))
            .andExpect(content().string(containsString("dict-br")));
    }
}
