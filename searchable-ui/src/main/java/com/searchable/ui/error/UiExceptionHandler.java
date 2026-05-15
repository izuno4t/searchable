package com.searchable.ui.error;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.util.NoSuchElementException;

/**
 * Maps domain exceptions to user-facing error pages for the UI layer.
 *
 * <p>Restricted via {@code basePackages} so REST controllers continue to be
 * handled by {@code GlobalExceptionHandler} in searchable-api.
 */
@ControllerAdvice(basePackages = "com.searchable.ui.controller")
@Controller
public class UiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(final NoSuchElementException e, final Model model,
                           final WebRequest request) {
        populate(model, 404, "Not Found", e.getMessage(), request);
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(final IllegalArgumentException e, final Model model,
                             final WebRequest request) {
        populate(model, 400, "Bad Request", e.getMessage(), request);
        return "error";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(final IllegalStateException e, final Model model,
                           final WebRequest request) {
        populate(model, 409, "Conflict", e.getMessage(), request);
        return "error";
    }

    private void populate(final Model model, final int status, final String error,
                          final String message, final WebRequest request) {
        model.addAttribute("activeNav", "");
        model.addAttribute("status", status);
        model.addAttribute("error", error);
        model.addAttribute("message", message);
        model.addAttribute("path", request.getDescription(false));
    }
}
