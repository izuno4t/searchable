package com.searchable.ui;


import com.searchable.testkit.spring.SearchableSpringBootTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@TestPropertySource(properties = {
    "searchable.data-directory=./build/ui-test",
    "searchable.persistence.url=jdbc:h2:mem:ui-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/ui-test/indexes"
})
class HomeControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void homePageRenders() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(containsString("Dashboard")))
            .andExpect(content().string(containsString("Searchable")));
    }

    @Test
    void navigationContainsManagementLinks() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Namespaces")))
            .andExpect(content().string(containsString("Indexes")))
            .andExpect(content().string(containsString("Settings")));
    }
}
