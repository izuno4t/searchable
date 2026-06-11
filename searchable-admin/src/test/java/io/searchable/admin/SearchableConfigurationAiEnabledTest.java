package io.searchable.admin;

import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.ai.SummaryConfig;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@code searchable.ai.enabled=true} branch of
 * {@code SearchableConfiguration.summaryConfig(...)} (lines 264-266), which
 * the default-config tests cannot reach because they boot with AI disabled.
 */
@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
@TestPropertySource(properties = {
    "searchable.ai.enabled=true",
    "searchable.ai.provider=openai",
    "searchable.ai.model=gpt-4o-mini"
})
class SearchableConfigurationAiEnabledTest {

    @Autowired
    SummaryConfig summaryConfig;

    @Test
    void aiEnabledPropagatesProviderAndModelToSummaryConfig() {
        assertThat(summaryConfig.providerName()).isEqualTo("openai");
        assertThat(summaryConfig.model()).isEqualTo("gpt-4o-mini");
        assertThat(summaryConfig.enabled()).isTrue();
    }
}
