package io.searchable.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the admin UI deployment. Scans both
 * {@code io.searchable.admin} (templates, view controllers) and
 * {@code io.searchable.api} (REST controllers, core wiring beans).
 */
@SpringBootApplication(scanBasePackages = {"io.searchable.admin", "io.searchable.api"})
public class SearchableUiApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SearchableUiApplication.class, args);
    }
}
