package io.searchable.admin;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Drives {@link SearchableUiApplication#main(String[])} without actually
 * booting Spring by stubbing the static factory.
 */
class SearchableUiApplicationTest {

    @Test
    void mainDelegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> sa = mockStatic(SpringApplication.class)) {
            sa.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                .thenReturn(null);
            SearchableUiApplication.main(new String[]{"--server.port=0"});
            sa.verify(() -> SpringApplication.run(
                SearchableUiApplication.class, new String[]{"--server.port=0"}));
        }
    }
}
