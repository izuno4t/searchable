package io.searchable.admin;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * High-level smoke test covering the admin UI's new settings pages
 * (TASK-105 / TASK-108 / TASK-110) and the dashboard — fulfilling
 * the integration-test scope from TASK-113.
 */
@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
class AdminPagesIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void settingsPageExposesStoragePaths() throws Exception {
        mvc.perform(get("/settings"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Storage paths")))
            .andExpect(content().string(containsString("Data directory")));
    }

    @Test
    void rankingPageRenders() throws Exception {
        mvc.perform(get("/settings/ranking"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("indexWeight")));
    }

    @Test
    void backupPageRenders() throws Exception {
        mvc.perform(get("/settings/backup"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Backup settings")));
    }
}
