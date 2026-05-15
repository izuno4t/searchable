package com.searchable.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
    "searchable.data-directory=./build/ui-idx-test",
    "searchable.persistence.url=jdbc:h2:mem:ui-idx-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/ui-idx-test/indexes"
})
class IndexViewControllerTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() throws Exception {
        mvc.perform(post("/namespaces/ns-idx/delete"));
        mvc.perform(post("/namespaces")
            .param("id", "ns-idx").param("name", "Index Test"));
    }

    @Test
    void overviewLists() throws Exception {
        mvc.perform(get("/indexes"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("ns-idx")));
    }

    @Test
    void detailShowsEmptyStateWithoutDocuments() throws Exception {
        mvc.perform(get("/indexes/ns-idx"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("ドキュメントが登録されていません")));
    }

    @Test
    void uploadFormRenders() throws Exception {
        mvc.perform(get("/documents/upload"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("ns-idx")));
    }

    @Test
    void uploadingMarkdownIndexesDocument() throws Exception {
        final MockMultipartFile file = new MockMultipartFile(
            "file", "sample.md", "text/markdown",
            "# Hello\n\n本文のサンプル".getBytes());

        mvc.perform(multipart("/documents/upload")
                .file(file)
                .param("namespaceId", "ns-idx"))
            .andExpect(redirectedUrl("/indexes/ns-idx"));

        mvc.perform(get("/indexes/ns-idx"))
            .andExpect(content().string(containsString("Hello")));
    }

    @Test
    void unsupportedFileTypeReturnsError() throws Exception {
        final MockMultipartFile file = new MockMultipartFile(
            "file", "binary.bin", "application/octet-stream",
            new byte[]{1, 2, 3});

        mvc.perform(multipart("/documents/upload")
                .file(file)
                .param("namespaceId", "ns-idx"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rebuildClearsIndex() throws Exception {
        final MockMultipartFile file = new MockMultipartFile(
            "file", "sample.md", "text/markdown",
            "# Title\n本文".getBytes());
        mvc.perform(multipart("/documents/upload")
            .file(file).param("namespaceId", "ns-idx"));

        mvc.perform(post("/indexes/ns-idx/rebuild"))
            .andExpect(redirectedUrl("/indexes/ns-idx"));

        mvc.perform(get("/indexes/ns-idx"))
            .andExpect(content().string(containsString("ドキュメントが登録されていません")));
    }
}
