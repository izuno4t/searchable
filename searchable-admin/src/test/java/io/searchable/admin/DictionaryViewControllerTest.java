package io.searchable.admin;

import org.junit.jupiter.api.BeforeEach;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.testkit.spring.SearchableSpringBootTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
@TestPropertySource(properties = {
    "searchable.dictionary.storage=db"
})
class DictionaryViewControllerTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() throws Exception {
        mvc.perform(post("/dictionaries/global/delete"));
        mvc.perform(post("/dictionaries/namespaces/dict-ns/delete"));
        mvc.perform(post("/namespaces/dict-ns/delete"));
        mvc.perform(post("/namespaces").param("id", "dict-ns").param("name", "Dict NS"));
    }

    @Test
    void listShowsGlobalRow() throws Exception {
        mvc.perform(get("/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Global")))
            .andExpect(content().string(containsString("dict-ns")));
    }

    @Test
    void editGlobalRendersForm() throws Exception {
        mvc.perform(get("/dictionaries/global/edit"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Entries (CSV)")));
    }

    @Test
    void saveGlobalPersistsEntries() throws Exception {
        mvc.perform(post("/dictionaries/global")
                .param("name", "Global Dict")
                .param("entriesCsv",
                    "関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,カスタム名詞\n"
                    + "朝青龍,朝青龍,アサショウリュウ,カスタム名詞"))
            .andExpect(redirectedUrl("/dictionaries"));

        mvc.perform(get("/dictionaries/global/edit"))
            .andExpect(content().string(containsString("関西国際空港")))
            .andExpect(content().string(containsString("朝青龍")));
    }

    @Test
    void duplicateSurfaceShowsError() throws Exception {
        mvc.perform(post("/dictionaries/global")
                .param("name", "Bad")
                .param("entriesCsv",
                    "X,X,ヨミ,POS\nX,X,ヨミ,POS"))
            .andExpect(status().isOk())
            .andExpect(view().name("dictionaries/edit"))
            .andExpect(content().string(containsString("duplicate surface form")));
    }

    @Test
    void invalidCsvShowsError() throws Exception {
        mvc.perform(post("/dictionaries/global")
                .param("name", "Bad")
                .param("entriesCsv", "not-enough,fields"))
            .andExpect(view().name("dictionaries/edit"))
            .andExpect(content().string(containsString("4 comma-separated fields")));
    }

    @Test
    void blankNameShowsValidationError() throws Exception {
        mvc.perform(post("/dictionaries/global")
                .param("name", "")
                .param("entriesCsv", ""))
            .andExpect(view().name("dictionaries/edit"))
            .andExpect(content().string(containsString("invalid-feedback")));
    }

    @Test
    void namespaceEditAndSaveRoundTrips() throws Exception {
        mvc.perform(post("/dictionaries/namespaces/dict-ns")
                .param("name", "NS Dict")
                .param("entriesCsv", "社内用語,社内 用語,シャナイ ヨウゴ,カスタム名詞"))
            .andExpect(redirectedUrl("/dictionaries"));

        mvc.perform(get("/dictionaries/namespaces/dict-ns/edit"))
            .andExpect(content().string(containsString("社内用語")));
    }

    @Test
    void deleteRemovesDictionary() throws Exception {
        mvc.perform(post("/dictionaries/global")
            .param("name", "G").param("entriesCsv", "x,x,X,POS"));
        mvc.perform(post("/dictionaries/global/delete"))
            .andExpect(redirectedUrl("/dictionaries"));
    }
}
