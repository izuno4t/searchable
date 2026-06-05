package io.searchable.admin;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.core.application.IndexService;
import io.searchable.core.domain.document.Document;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
class IndexViewControllerBranchTest {

    @Autowired MockMvc mvc;
    @Autowired IndexService indexService;

    @BeforeEach
    void seed() throws Exception {
        mvc.perform(post("/namespaces/ns-br/delete"));
        mvc.perform(post("/namespaces").param("id", "ns-br").param("name", "Br"));
    }

    @Test
    void deleteMissingDocumentReturnsWarningFlash() throws Exception {
        mvc.perform(post("/indexes/ns-br/documents/never-existed/delete"))
            .andExpect(redirectedUrl("/indexes/ns-br"))
            .andExpect(flash().attributeExists("flashWarning"));
    }

    @Test
    void deleteExistingDocumentReturnsSuccessFlash() throws Exception {
        // Index a document directly through the shared IndexService bean so
        // the namespace context is open in the same Lucene provider that
        // the controller will use when it forwards the delete request.
        indexService.index(Document.builder()
            .id("knownDocId").namespaceId("ns-br")
            .title("t").content("body")
            .indexedAt(Instant.now()).build());

        mvc.perform(post("/indexes/ns-br/documents/knownDocId/delete"))
            .andExpect(redirectedUrl("/indexes/ns-br"))
            .andExpect(flash().attributeExists("flashSuccess"));
    }

    @Test
    void detailRendersWithNegativePageNormalised() throws Exception {
        mvc.perform(get("/indexes/ns-br").param("page", "-5"))
            .andExpect(status().isOk());
    }
}
