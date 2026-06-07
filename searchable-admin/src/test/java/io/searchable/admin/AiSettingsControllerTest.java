package io.searchable.admin;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.ai.SummaryConfigProvider;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
class AiSettingsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SummaryConfigProvider summaryConfigProvider;

    @Test
    void formPage_rendersDiscoveredProvidersAndCurrentConfig() throws Exception {
        mvc.perform(get("/settings/ai"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("AI Summarisation Settings")))
            // Bundled providers should appear in the <select>
            .andExpect(content().string(containsString("openai")))
            .andExpect(content().string(containsString("anthropic")))
            .andExpect(content().string(containsString("ollama")));
    }

    @Test
    void post_updatesProviderInHolder() throws Exception {
        mvc.perform(post("/settings/ai")
                .param("enabled", "true")
                .param("provider", "ollama")
                .param("model", "llama3.2")
                .param("timeoutSeconds", "20")
                .param("maxTokens", "256")
                .param("temperature", "0.5")
                .param("maxContextItems", "3")
                .param("maxContextChars", "4000")
                .param("fallbackOnError", "true"))
            .andExpect(redirectedUrl("/settings/ai"));

        final var current = summaryConfigProvider.current();
        assertThat(current.enabled()).isTrue();
        assertThat(current.providerName()).isEqualTo("ollama");
        assertThat(current.model()).isEqualTo("llama3.2");
        assertThat(current.timeout().getSeconds()).isEqualTo(20L);
        assertThat(current.maxTokens()).isEqualTo(256);
        assertThat(current.temperature()).isEqualTo(0.5);
        assertThat(current.maxContextItems()).isEqualTo(3);
        assertThat(current.maxContextChars()).isEqualTo(4000);

        // Reset to disabled for subsequent tests in this class.
        summaryConfigProvider.update(io.searchable.ai.SummaryConfig.disabled());
    }

    @Test
    void post_rejectsTemperatureAboveBound() throws Exception {
        mvc.perform(post("/settings/ai")
                .param("enabled", "true")
                .param("provider", "openai")
                .param("timeoutSeconds", "15")
                .param("maxTokens", "256")
                .param("temperature", "3.5")
                .param("maxContextItems", "3")
                .param("maxContextChars", "4000")
                .param("fallbackOnError", "true"))
            // Validation error returns the form view, not a redirect.
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("AI Summarisation Settings")));
    }
}
