package io.searchable.example.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.example.api.config.SearchableTestDataConfig;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchableApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @Order(1)
    void createNamespaceReturns201() throws Exception {
        final Map<String, Object> body = Map.of(
            "id", "it-ns",
            "name", "Integration Test",
            "config", Map.of("architecture", "FULL_TEXT")
        );

        mvc.perform(post("/api/v1/namespaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", equalTo("it-ns")))
            .andExpect(jsonPath("$.name", equalTo("Integration Test")));
    }

    @Test
    @Order(2)
    void indexDocumentReturns201() throws Exception {
        final Map<String, Object> body = Map.of(
            "namespaceId", "it-ns",
            "document", Map.of(
                "id", "doc-1",
                "title", "REST APIテスト",
                "content", "Searchable は日本語形態素解析に対応した全文検索ライブラリです。"
            )
        );

        mvc.perform(post("/api/v1/index/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", equalTo("doc-1")))
            .andExpect(jsonPath("$.status", equalTo("INDEXED")));
    }

    @Test
    @Order(3)
    void searchFindsIndexedDocument() throws Exception {
        final Map<String, Object> body = Map.of(
            "query", "形態素解析",
            "namespaceIds", List.of("it-ns")
        );

        mvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalHits", equalTo(1)))
            .andExpect(jsonPath("$.hits", hasSize(1)))
            .andExpect(jsonPath("$.hits[0].id", equalTo("doc-1")));
    }

    @Test
    @Order(4)
    void listingShowsCreatedNamespace() throws Exception {
        mvc.perform(get("/api/v1/namespaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.namespaces[*].id", org.hamcrest.Matchers.hasItem("it-ns")));
    }

    @Test
    @Order(5)
    void getReturnsNamespaceDetail() throws Exception {
        mvc.perform(get("/api/v1/namespaces/it-ns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", equalTo("it-ns")));
    }

    @Test
    @Order(6)
    void missingNamespaceReturns404() throws Exception {
        mvc.perform(get("/api/v1/namespaces/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", equalTo("NOT_FOUND")));
    }

    @Test
    @Order(7)
    void deleteNamespaceReturns204() throws Exception {
        mvc.perform(delete("/api/v1/namespaces/it-ns"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/namespaces/it-ns"))
            .andExpect(status().isNotFound());
    }
}
