package io.searchable.admin;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.core.application.IndexService;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
class AdminFinalBranchTest {

    @Autowired MockMvc mvc;
    @Autowired NamespaceService namespaceService;
    @Autowired DataSource dataSource;

    @BeforeEach
    void seed() {
        namespaceService.findById("fb-ns").ifPresent(n -> namespaceService.delete("fb-ns"));
        namespaceService.create("fb-ns", "FB", NamespaceConfigPatch.empty());
    }

    @Test
    void updateWithSameNameSkipsRenameCall() throws Exception {
        // Posts the existing name unchanged -> the `!equals(form.name)` false
        // branch fires, rename() is skipped.
        mvc.perform(post("/namespaces/fb-ns").param("name", "FB"))
            .andExpect(redirectedUrl("/namespaces"));
    }

    @Test
    void uploadWithExplicitFilenameUsesProvidedName() throws Exception {
        // file.getOriginalFilename() != null -> takes the non-fallback ternary side.
        final MockMultipartFile file = new MockMultipartFile(
            "file", "explicit.txt", "text/plain", "hi".getBytes());
        mvc.perform(multipart("/documents/upload")
                .file(file).param("namespaceId", "fb-ns"))
            .andExpect(redirectedUrl("/indexes/fb-ns"));
    }

    @Test
    void indexesOverviewHandlesNamespaceWithoutMetadata() throws Exception {
        // Delete the metadata row directly so getMetadata() throws and the
        // catch branch builds an IndexOverviewRow with metadata=null.
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM INDEX_METADATA WHERE NAMESPACE_ID='fb-ns'");
        }
        mvc.perform(get("/indexes"))
            .andExpect(status().isOk());
    }
}
