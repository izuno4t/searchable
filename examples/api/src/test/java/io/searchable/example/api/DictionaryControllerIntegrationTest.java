package io.searchable.example.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
    "searchable.data-directory=./build/dict-it",
    "searchable.persistence.url=jdbc:h2:mem:dict-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/dict-it/indexes",
    "searchable.dictionary.storage=db"
})
class DictionaryControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @Order(1)
    void putGlobalCreatesDictionary() throws Exception {
        final Map<String, Object> body = Map.of(
            "name", "Global",
            "entries", List.of(Map.of(
                "surface", "Lucene",
                "segmentation", "Lucene",
                "reading", "ルシーン",
                "pos", "カスタム名詞")));

        mvc.perform(put("/api/v1/dictionaries/global")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope", equalTo("GLOBAL")))
            .andExpect(jsonPath("$.entries", hasSize(1)));
    }

    @Test
    @Order(2)
    void getGlobalReturnsCurrentDictionary() throws Exception {
        mvc.perform(get("/api/v1/dictionaries/global"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].surface", equalTo("Lucene")));
    }

    @Test
    @Order(3)
    void putNamespaceCreatesNamespaceDictionary() throws Exception {
        final Map<String, Object> body = Map.of(
            "name", "Project A",
            "entries", List.of(Map.of(
                "surface", "社内用語",
                "segmentation", "社内 用語",
                "reading", "シャナイ ヨウゴ",
                "pos", "カスタム名詞")));

        mvc.perform(put("/api/v1/dictionaries/namespaces/project-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope", equalTo("NAMESPACE:project-a")));
    }

    @Test
    @Order(4)
    void listIncludesBothScopes() throws Exception {
        mvc.perform(get("/api/v1/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", equalTo(2)))
            .andExpect(jsonPath("$.dictionaries[*].scope",
                hasItem("GLOBAL")))
            .andExpect(jsonPath("$.dictionaries[*].scope",
                hasItem("NAMESPACE:project-a")));
    }

    @Test
    @Order(5)
    void missingNamespaceDictionaryReturns404() throws Exception {
        mvc.perform(get("/api/v1/dictionaries/namespaces/does-not-exist"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void invalidEntryBodyReturns400() throws Exception {
        final Map<String, Object> body = Map.of(
            "name", "Bad",
            "entries", List.of(Map.of("surface", "", "segmentation", "x",
                "reading", "y", "pos", "z")));
        mvc.perform(put("/api/v1/dictionaries/global")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void deleteRemovesDictionary() throws Exception {
        mvc.perform(delete("/api/v1/dictionaries/global"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/dictionaries/global"))
            .andExpect(status().isNotFound());
    }
}
