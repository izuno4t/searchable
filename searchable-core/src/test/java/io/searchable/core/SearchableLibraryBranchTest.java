package io.searchable.core;

import io.searchable.core.application.config.SearchableConfig;
import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.application.config.IndexConfig;
import io.searchable.core.application.config.PluginsConfig;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Branch coverage for the {@link SearchableLibrary.Builder} default paths. */
class SearchableLibraryBranchTest {

    @TempDir Path tempDir;

    private SearchableConfig appConfig() {
        return new SearchableConfig(
            tempDir,
            new PersistenceConfig("H2", "jdbc:h2:mem:branch-test;DB_CLOSE_DELAY=-1", "sa", ""),
            new IndexConfig(tempDir.resolve("idx")),
            PluginsConfig.classpathOnly(),
            SearchableGlobalConfig.defaults());
    }

    @Test
    void readOnlyLibraryRefusesWriteServices() {
        try (SearchableLibrary lib = SearchableLibrary.builder()
                .applicationConfig(appConfig())
                .readOnly(true)
                .build()) {
            assertThat(lib.isReadOnly()).isTrue();
            assertThat(lib.searchService()).isNotNull();
            assertThatThrownBy(lib::indexService).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(lib::namespaceService).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void defaultDataSourceIsCreatedAndClosedWhenNotProvided() {
        // Triggers the "dataSource == null" branch and the
        // registerCloseable(...) AutoCloseable lambda path.
        try (SearchableLibrary lib = SearchableLibrary.builder()
                .applicationConfig(appConfig())
                .build()) {
            assertThat(lib.indexService()).isNotNull();
        }
    }

    @Test
    void globalConfigOverloadInstallsProvider() {
        try (SearchableLibrary lib = SearchableLibrary.builder()
                .applicationConfig(appConfig())
                .globalConfig(SearchableGlobalConfig.defaults())
                .build()) {
            assertThat(lib.globalConfigProvider().current()).isNotNull();
        }
    }
}
