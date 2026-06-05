package io.searchable.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.testkit.spring.SearchableSpringBootTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
class DashboardTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void seed() throws Exception {
        mvc.perform(post("/namespaces/dash-ns/delete"));
        mvc.perform(post("/namespaces").param("id", "dash-ns").param("name", "Dash"));
        mvc.perform(post("/api/v1/index/documents")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "namespaceId", "dash-ns",
                "document", Map.of("id", "d1", "title", "Hello", "content", "本文")))));
    }

    @Test
    void dashboardShowsMetrics() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Namespaces")))
            .andExpect(content().string(containsString("Documents")));
    }

    @Test
    void recentSearchesAppearOnDashboard() throws Exception {
        mvc.perform(post("/api/v1/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "query", "本文",
                "namespaceIds", List.of("dash-ns")))));

        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("samples:")));
    }
}
