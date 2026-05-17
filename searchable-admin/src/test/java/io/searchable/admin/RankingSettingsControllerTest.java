package io.searchable.admin;

import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@TestPropertySource(properties = {
    "searchable.data-directory=./build/ui-ranking-test",
    "searchable.persistence.url=jdbc:h2:mem:ui-ranking-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/ui-ranking-test/indexes"
})
class RankingSettingsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired NamespaceService namespaceService;

    @BeforeEach
    void setUp() {
        if (!namespaceService.findById("rk-ns").isPresent()) {
            namespaceService.create("rk-ns", "Ranking NS", NamespaceConfigPatch.empty());
        }
    }

    @AfterEach
    void tearDown() {
        namespaceService.findById("rk-ns").ifPresent(n -> namespaceService.delete(n.id()));
    }

    @Test
    void indexPageListsNamespacesAndForm() throws Exception {
        mvc.perform(get("/settings/ranking"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ranking settings")))
            .andExpect(content().string(containsString("rk-ns")));
    }

    @Test
    void postUpdatesIndexWeightForSelectedNamespace() throws Exception {
        mvc.perform(post("/settings/ranking")
                .param("namespaceId", "rk-ns")
                .param("indexWeight", "2.5"))
            .andExpect(redirectedUrl("/settings/ranking"));

        final var updated = namespaceService.findById("rk-ns").orElseThrow();
        assertThat(updated.config().indexWeight()).isEqualTo(2.5);
    }
}
