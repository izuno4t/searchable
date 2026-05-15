package com.searchable.ui;

import org.junit.jupiter.api.BeforeEach;

import com.searchable.testkit.spring.SearchableSpringBootTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SearchableSpringBootTest
@TestPropertySource(properties = {
    "searchable.data-directory=./build/ui-ns-test",
    "searchable.persistence.url=jdbc:h2:mem:ui-ns-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/ui-ns-test/indexes"
})
class NamespaceViewControllerTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void deleteIfExists() throws Exception {
        // Reset between tests; in-memory H2 persists for the JVM.
        mvc.perform(post("/namespaces/ns-x/delete"));
    }

    @Test
    void emptyListPageIsShown() throws Exception {
        mvc.perform(get("/namespaces"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Namespace が登録されていません")));
    }

    @Test
    void createFormRenders() throws Exception {
        mvc.perform(get("/namespaces/new"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("New Namespace")))
            .andExpect(content().string(containsString("Architecture")));
    }

    @Test
    void invalidCreateShowsValidationErrors() throws Exception {
        mvc.perform(post("/namespaces")
                .param("id", "Invalid ID!")
                .param("name", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("namespaces/create"))
            .andExpect(content().string(containsString("invalid-feedback")));
    }

    @Test
    void validCreateRedirectsToList() throws Exception {
        mvc.perform(post("/namespaces")
                .param("id", "ns-x")
                .param("name", "Test Namespace"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/namespaces"))
            .andExpect(flash().attributeExists("flashSuccess"));

        mvc.perform(get("/namespaces"))
            .andExpect(content().string(containsString("ns-x")));
    }

    @Test
    void editFormPrePopulates() throws Exception {
        mvc.perform(post("/namespaces").param("id", "ns-x").param("name", "Edit Me"));

        mvc.perform(get("/namespaces/ns-x/edit"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Edit Me")));
    }

    @Test
    void updateChangesName() throws Exception {
        mvc.perform(post("/namespaces").param("id", "ns-x").param("name", "Original"));

        mvc.perform(post("/namespaces/ns-x").param("name", "Renamed"))
            .andExpect(redirectedUrl("/namespaces"));

        mvc.perform(get("/namespaces"))
            .andExpect(content().string(containsString("Renamed")));
    }

    @Test
    void deletePostRemovesNamespace() throws Exception {
        mvc.perform(post("/namespaces").param("id", "ns-x").param("name", "Doomed"));

        mvc.perform(post("/namespaces/ns-x/delete"))
            .andExpect(redirectedUrl("/namespaces"));

        mvc.perform(get("/namespaces"))
            .andExpect(content().string(containsString("Namespace が登録されていません")));
    }

    @Test
    void missingNamespaceReturnsErrorPage() throws Exception {
        mvc.perform(get("/namespaces/does-not-exist/edit"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error"));
    }
}
