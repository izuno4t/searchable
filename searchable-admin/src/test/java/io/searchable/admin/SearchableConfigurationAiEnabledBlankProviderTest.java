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
 * Covers the {@code searchable.ai.enabled=true} + blank provider branch of
 * {@code SearchableConfiguration.summaryConfig}: {@code a.isEnabled()} is true
 * but {@code a.getProvider().isBlank()} is true, so {@code providerName} ends
 * up null and the resulting config is treated as disabled.
 */
@SearchableSpringBootTest
@Import(SearchableTestDataConfig.class)
@TestPropertySource(properties = {
    "searchable.ai.enabled=true",
    "searchable.ai.provider="
})
class SearchableConfigurationAiEnabledBlankProviderTest {

    @Autowired SummaryConfig summaryConfig;

    @Test
    void enabledFlagWithoutProviderProducesDisabledConfig() {
        assertThat(summaryConfig.providerName()).isNull();
        assertThat(summaryConfig.enabled()).isFalse();
    }
}
