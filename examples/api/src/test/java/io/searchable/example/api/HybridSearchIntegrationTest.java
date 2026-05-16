package io.searchable.example.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for vector and hybrid search via the REST API.
 */
@SearchableSpringBootTest
@TestPropertySource(properties = {
    "searchable.data-directory=./build/hybrid-test",
    "searchable.persistence.url=jdbc:h2:mem:hybrid-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/hybrid-test/indexes",
    "searchable.embedding.dimension=128"
})
class HybridSearchIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void seed() throws Exception {
        // Drop any leftover state from the previous test (the in-memory H2
        // DB and Lucene directory survive between methods within a class).
        mvc.perform(delete("/api/v1/namespaces/hyb"));

        mvc.perform(post("/api/v1/namespaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "id", "hyb",
                    "name", "Hybrid",
                    "config", Map.of("architecture", "HYBRID", "searchStrategy", "PARALLEL")))))
            .andExpect(status().isCreated());

        final List<Map<String, Object>> docs = List.of(
            Map.of("id", "d1", "title", "全文検索の解説",
                "content", "Apache Lucene による全文検索エンジンの基本"),
            Map.of("id", "d2", "title", "ベクトル検索",
                "content", "意味的類似度に基づく検索手法"),
            Map.of("id", "d3", "title", "ハイブリッド検索",
                "content", "全文検索 と ベクトル検索 を組み合わせる")
        );

        mvc.perform(post("/api/v1/index/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("namespaceId", "hyb", "documents", docs))))
            .andExpect(status().isOk());
    }

    @Test
    void fullTextRoutesWhenSearchTypeOverride() throws Exception {
        mvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "query", "全文検索",
                    "namespaceIds", List.of("hyb"),
                    "searchType", "FULL_TEXT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.hits[*].id", hasItem("d1")));
    }

    @Test
    void vectorSearchReturnsHits() throws Exception {
        mvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "query", "Apache Lucene による全文検索エンジンの基本",
                    "namespaceIds", List.of("hyb"),
                    "searchType", "VECTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.hits[0].id").value("d1"));
    }

    @Test
    void hybridParallelSearchProducesUnion() throws Exception {
        mvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "query", "全文検索",
                    "namespaceIds", List.of("hyb"),
                    "searchType", "HYBRID"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.hits[*].id", hasItem("d1")));
    }

    @Test
    void implicitTypeFollowsNamespaceArchitecture() throws Exception {
        // Namespace was created with architecture=HYBRID.
        mvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "query", "ハイブリッド",
                    "namespaceIds", List.of("hyb")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)));
    }
}
