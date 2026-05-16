package io.searchable.example.api.security;

import io.searchable.example.api.config.SearchableProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Servlet filter that enforces the {@code X-API-Key} header when the API
 * key is configured (TASK-126).
 *
 * <p>The filter is a no-op when the key is unset, supporting development
 * and embedded use without authentication.
 */
@Component
public final class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private final SearchableProperties properties;

    public ApiKeyFilter(final SearchableProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        if (!properties.getApi().isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        final String supplied = request.getHeader(HEADER);
        if (!Objects.equals(supplied, properties.getApi().getKey())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"error\":{\"code\":\"INVALID_API_KEY\","
                    + "\"message\":\"Missing or incorrect X-API-Key header.\"}}");
            return;
        }
        chain.doFilter(request, response);
    }
}
