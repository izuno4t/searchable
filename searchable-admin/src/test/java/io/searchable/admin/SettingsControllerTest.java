package io.searchable.admin;

import io.searchable.core.application.config.GlobalConfigProvider;

import io.searchable.testkit.spring.SearchableSpringBootTest;

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
    "searchable.data-directory=./build/ui-set-test",
    "searchable.persistence.url=jdbc:h2:mem:ui-set-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/ui-set-test/indexes"
})
class SettingsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired GlobalConfigProvider provider;

    @Test
    void settingsFormShowsCurrentValues() throws Exception {
        mvc.perform(get("/settings"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Default Architecture")))
            .andExpect(content().string(containsString("FULL_TEXT")));
    }

    @Test
    void savingNewDefaultsUpdatesProvider() throws Exception {
        mvc.perform(post("/settings")
                .param("defaultArchitecture", "HYBRID")
                .param("defaultSearchStrategy", "PARALLEL")
                .param("defaultSearchOrder", "VECTOR_FIRST"))
            .andExpect(redirectedUrl("/settings"));

        assertThat(provider.current().defaultArchitecture().name()).isEqualTo("HYBRID");
        assertThat(provider.current().defaultSearchStrategy().name()).isEqualTo("PARALLEL");
        assertThat(provider.current().defaultSearchOrder().name()).isEqualTo("VECTOR_FIRST");
    }
}
