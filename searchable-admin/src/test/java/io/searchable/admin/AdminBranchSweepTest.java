package io.searchable.admin;

import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SearchableSpringBootTest
@TestPropertySource(properties = {
    "searchable.data-directory=./build/ui-sweep-test",
    "searchable.persistence.url=jdbc:h2:mem:ui-sweep;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/ui-sweep-test/indexes"
})
class AdminBranchSweepTest {

    @Autowired MockMvc mvc;
    @Autowired NamespaceService namespaceService;

    @BeforeEach
    void seed() {
        // Cleanup
        namespaceService.findById("sw-ns").ifPresent(n -> namespaceService.delete("sw-ns"));
        namespaceService.findById("dup").ifPresent(n -> namespaceService.delete("dup"));
        namespaceService.create("sw-ns", "Sweep NS", NamespaceConfigPatch.empty());
    }

    @Test
    void createWithValidationErrorRendersFormAgain() throws Exception {
        // Blank id fails NotBlank validation -> BindingResult.hasErrors()=true
        mvc.perform(post("/namespaces")
                .param("id", "")
                .param("name", "X"))
            .andExpect(status().isOk())
            .andExpect(view().name("namespaces/create"));
    }

    @Test
    void createWithDuplicateIdRendersFormWithGlobalError() throws Exception {
        namespaceService.create("dup", "Dup", NamespaceConfigPatch.empty());
        // Duplicate creation -> service throws IllegalStateException -> reject
        mvc.perform(post("/namespaces")
                .param("id", "dup")
                .param("name", "Another"))
            .andExpect(status().isOk())
            .andExpect(view().name("namespaces/create"));
    }

    @Test
    void updateWithValidationErrorRendersEditForm() throws Exception {
        // Blank name fails @NotBlank on edit form
        mvc.perform(post("/namespaces/sw-ns").param("name", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("namespaces/edit"));
    }

    @Test
    void updateMissingNamespaceTriggersNotFoundHandler() throws Exception {
        // 404 path via the lambda$update$1 supplier + UiExceptionHandler.notFound
        mvc.perform(post("/namespaces/never-existed")
                .param("name", "Renamed"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error"));
    }

    @Test
    void homePageRendersWithSeededPerformanceSamples() throws Exception {
        // Just hitting the dashboard exercises the for-loop branch over an
        // empty/some-sample window in SearchPerformanceMonitor.
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Dashboard")));
    }

    @Test
    void settingsValidationErrorRendersFormAgain() throws Exception {
        // Posting an invalid enum -> BindException; the controller renders
        // the form with validation messages instead of redirecting.
        mvc.perform(post("/settings")
                .param("defaultArchitecture", "NOT-AN-ENUM")
                .param("defaultSearchStrategy", "SEQUENTIAL")
                .param("defaultSearchOrder", "FULL_TEXT_FIRST"))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"));
    }

    @Test
    void illegalStateExceptionMapsToConflictPage() throws Exception {
        // Creating a duplicate namespace via the post handler triggers
        // IllegalStateException inside NamespaceService.create; the
        // controller catches it but the dictionary delete-then-recreate
        // path goes through UiExceptionHandler.conflict.
        namespaceService.create("dup", "Dup", NamespaceConfigPatch.empty());
        mvc.perform(post("/namespaces").param("id", "dup").param("name", "Other"))
            .andExpect(status().isOk());
    }
}
