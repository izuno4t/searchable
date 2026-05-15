package com.searchable.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the admin UI deployment. Scans both
 * {@code com.searchable.ui} (templates, view controllers) and
 * {@code com.searchable.api} (REST controllers, core wiring beans).
 */
@SpringBootApplication(scanBasePackages = {"com.searchable.ui", "com.searchable.api"})
public class SearchableUiApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SearchableUiApplication.class, args);
    }
}
