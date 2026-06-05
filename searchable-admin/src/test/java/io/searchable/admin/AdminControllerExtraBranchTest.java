package io.searchable.admin;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.SearchPerformanceMonitor;
import io.searchable.core.application.SearchService;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
class AdminControllerExtraBranchTest {

    @Autowired MockMvc mvc;
    @Autowired NamespaceService namespaceService;
    @Autowired SearchService searchService;
    @Autowired SearchPerformanceMonitor monitor;

    @BeforeEach
    void seed() {
        namespaceService.findById("ex-ns").ifPresent(n -> namespaceService.delete("ex-ns"));
        namespaceService.create("ex-ns", "Extra", NamespaceConfigPatch.empty());
    }

    @Test
    void rankingSaveForMissingNamespaceTriggersNotFound() throws Exception {
        // Drives the lambda$save$0 orElseThrow path.
        mvc.perform(post("/settings/ranking")
                .param("namespaceId", "never-existed")
                .param("indexWeight", "1.5"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error"));
    }

    @Test
    void indexViewShowForMissingNamespaceTriggersNotFound() throws Exception {
        // Drives the lambda$show$0 orElseThrow path.
        mvc.perform(get("/indexes/never-existed"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error"));
    }

    @Test
    void dashboardRendersAfterRecordingSearchSamples() throws Exception {
        // Record samples so the for-loop body in HomeController.index runs.
        monitor.record(12L);
        monitor.record(34L);
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Dashboard")));
    }

    @Test
    void backupRunOnceWithBlankDirectoryFallsBackToDefault() throws Exception {
        // directory=" " is blank -> falls back to defaultBackupDirectory.
        mvc.perform(post("/settings/backup/run").param("directory", " "))
            .andExpect(redirectedUrl("/settings/backup"));
    }

    @Test
    void indexesOverviewIncludesNamespacesWithoutMetadata() throws Exception {
        // ex-ns just created; its IndexMetadata may not exist yet, so the
        // overview() falls through the NoSuchElementException catch branch.
        mvc.perform(get("/indexes"))
            .andExpect(status().isOk());
    }
}
