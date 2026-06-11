package io.searchable.admin;

import io.searchable.admin.controller.AiSettingsController;
import io.searchable.admin.form.AiSettingsForm;
import io.searchable.ai.AiProviderRegistry;
import io.searchable.ai.SummaryConfig;
import io.searchable.ai.SummaryConfigProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives the defensive {@code catch (IllegalArgumentException)} branch in
 * {@link AiSettingsController#save} that the MockMvc tests cannot reach,
 * because the Jakarta validation constraints on {@link AiSettingsForm} reject
 * out-of-range input before the controller body runs. We invoke the
 * controller directly with a stubbed {@link SummaryConfigProvider} that
 * throws on {@code update(...)}, so the rendered view + flash error are
 * observable.
 */
class AiSettingsControllerErrorBranchTest {

    @Test
    void save_renderingsFormWithFlashErrorWhenConfigProviderRejectsUpdate() {
        final AiProviderRegistry registry = mock(AiProviderRegistry.class);
        when(registry.names()).thenReturn(Set.of("ollama"));
        final SummaryConfigProvider holder = mock(SummaryConfigProvider.class);
        doThrow(new IllegalArgumentException("synthetic policy rejection"))
            .when(holder).update(org.mockito.ArgumentMatchers.any(SummaryConfig.class));

        final AiSettingsController controller = new AiSettingsController(registry, holder);
        final AiSettingsForm form = new AiSettingsForm();
        form.setEnabled(true);
        form.setProvider("ollama");
        form.setModel("llama3.2");
        // Other fields use defaults baked into the form class.
        final BindingResult result = new BeanPropertyBindingResult(form, "form");
        final ConcurrentModel model = new ConcurrentModel();
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        final String view = controller.save(form, result, model, flash);

        assertThat(view).isEqualTo("ai-settings");
        assertThat(model.getAttribute("flashError"))
            .isEqualTo("synthetic policy rejection");
        assertThat(model.getAttribute("registeredProviders")).isNotNull();
    }

    @Test
    void save_returnsFormViewWhenBindingHasValidationErrors() {
        // BindingResult.hasErrors=true short-circuit: should re-render the
        // form without touching the holder.
        final AiProviderRegistry registry = mock(AiProviderRegistry.class);
        when(registry.names()).thenReturn(Set.of());
        final SummaryConfigProvider holder = mock(SummaryConfigProvider.class);

        final AiSettingsController controller = new AiSettingsController(registry, holder);
        final AiSettingsForm form = new AiSettingsForm();
        final BindingResult result = new BeanPropertyBindingResult(form, "form");
        result.reject("synthetic");
        final ConcurrentModel model = new ConcurrentModel();
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        final String view = controller.save(form, result, model, flash);

        assertThat(view).isEqualTo("ai-settings");
        assertThat(model.getAttribute("hasProviders")).isEqualTo(false);
    }

    @Test
    void prepare_modelExposesEmptyProvidersListWhenRegistryHasNone() {
        // Covers the false branch of `!names.isEmpty()` in AiSettingsController.
        final AiProviderRegistry registry = mock(AiProviderRegistry.class);
        when(registry.names()).thenReturn(Set.of());
        final SummaryConfigProvider holder = mock(SummaryConfigProvider.class);
        when(holder.current()).thenReturn(SummaryConfig.disabled());

        final AiSettingsController controller = new AiSettingsController(registry, holder);
        final ConcurrentModel model = new ConcurrentModel();

        final String view = controller.form(model);

        assertThat(view).isEqualTo("ai-settings");
        assertThat(model.getAttribute("hasProviders")).isEqualTo(false);
        assertThat(model.getAttribute("registeredProviders"))
            .isEqualTo(java.util.List.of());
    }
}
