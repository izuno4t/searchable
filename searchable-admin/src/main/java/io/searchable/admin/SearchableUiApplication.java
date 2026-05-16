package io.searchable.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the settings &amp; operations Web application
 * ({@code searchable-admin}). Scans {@code io.searchable.admin} which
 * owns the view controllers, forms and Spring configuration that wires
 * the searchable-core services as Spring beans.
 */
@SpringBootApplication(scanBasePackages = "io.searchable.admin")
public class SearchableUiApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SearchableUiApplication.class, args);
    }
}
