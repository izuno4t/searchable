package io.searchable.admin;

import io.searchable.admin.error.UiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.context.request.WebRequest;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Direct tests for the three @ExceptionHandler methods. */
class UiExceptionHandlerTest {

    private final UiExceptionHandler handler = new UiExceptionHandler();
    private final WebRequest request = mock(WebRequest.class);

    UiExceptionHandlerTest() {
        when(request.getDescription(false)).thenReturn("uri=/x");
    }

    @Test
    void notFoundPopulatesModel() {
        final Model model = new ConcurrentModel();
        final String view = handler.notFound(
            new NoSuchElementException("missing"), model, request);
        assertThat(view).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(404);
        assertThat(model.getAttribute("error")).isEqualTo("Not Found");
        assertThat(model.getAttribute("message")).isEqualTo("missing");
    }

    @Test
    void badRequestPopulatesModel() {
        final Model model = new ConcurrentModel();
        final String view = handler.badRequest(
            new IllegalArgumentException("bad input"), model, request);
        assertThat(view).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(400);
        assertThat(model.getAttribute("error")).isEqualTo("Bad Request");
    }

    @Test
    void conflictPopulatesModel() {
        final Model model = new ConcurrentModel();
        final String view = handler.conflict(
            new IllegalStateException("clash"), model, request);
        assertThat(view).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(409);
        assertThat(model.getAttribute("error")).isEqualTo("Conflict");
        assertThat(model.getAttribute("message")).isEqualTo("clash");
    }
}
