package io.searchable.example.api.security;

import io.searchable.example.api.config.SearchableProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configurable CORS support driven by {@code searchable.cors.*} (TASK-127).
 *
 * <p>Activated only when at least one allowed origin is configured; the
 * default empty list keeps the API single-origin for production safety.
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(final SearchableProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(final CorsRegistry registry) {
                final var cors = properties.getCors();
                if (cors.getAllowedOrigins() == null || cors.getAllowedOrigins().isEmpty()) {
                    return;
                }
                registry.addMapping("/api/**")
                    .allowedOrigins(cors.getAllowedOrigins().toArray(String[]::new))
                    .allowedMethods(cors.getAllowedMethods().toArray(String[]::new))
                    .allowedHeaders(cors.getAllowedHeaders().toArray(String[]::new))
                    .allowCredentials(cors.isAllowCredentials());
            }
        };
    }
}
