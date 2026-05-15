package com.searchable.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Error envelope sent to clients on failure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, Object> details, Instant timestamp) {

    public static ApiError of(final String code, final String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError of(final String code, final String message,
                              final Map<String, Object> details) {
        return new ApiError(code, message, details, Instant.now());
    }
}
