package io.searchable.example.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Searchable REST API.
 */
@SpringBootApplication
public class SearchableApiApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SearchableApiApplication.class, args);
    }
}
