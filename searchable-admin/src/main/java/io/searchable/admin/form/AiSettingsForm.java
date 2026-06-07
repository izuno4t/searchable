package io.searchable.admin.form;

import java.time.Duration;

import io.searchable.ai.SummaryConfig;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Form-binding object for the AI summarisation settings page
 * ({@code /settings/ai}).
 *
 * <p>Holds a mutable representation of {@link SummaryConfig}. Convertible via
 * {@link #toSummaryConfig()} once {@link jakarta.validation.Valid} passes.
 */
public class AiSettingsForm {

    private boolean enabled;
    private String provider = "";
    private String model = "";

    @NotNull
    @Min(1)
    private Long timeoutSeconds = 15L;

    @Min(1)
    private int maxTokens = 512;

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.2;

    @Min(1)
    private int maxContextItems = 5;

    @Min(100)
    private int maxContextChars = 8000;

    private boolean fallbackOnError = true;

    public static AiSettingsForm from(final SummaryConfig config) {
        final AiSettingsForm form = new AiSettingsForm();
        form.enabled = config.enabled();
        form.provider = config.providerName() == null ? "" : config.providerName();
        form.model = config.model() == null ? "" : config.model();
        form.timeoutSeconds = config.timeout().getSeconds();
        form.maxTokens = config.maxTokens();
        form.temperature = config.temperature();
        form.maxContextItems = config.maxContextItems();
        form.maxContextChars = config.maxContextChars();
        form.fallbackOnError = config.fallbackOnError();
        return form;
    }

    public SummaryConfig toSummaryConfig() {
        final String providerName = enabled && !provider.isBlank() ? provider : null;
        final String modelName = model.isBlank() ? null : model;
        return new SummaryConfig(
            providerName,
            modelName,
            Duration.ofSeconds(timeoutSeconds),
            maxTokens,
            temperature,
            maxContextItems,
            maxContextChars,
            fallbackOnError
        );
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(final boolean v) { this.enabled = v; }
    public String getProvider() { return provider; }
    public void setProvider(final String v) { this.provider = v == null ? "" : v; }
    public String getModel() { return model; }
    public void setModel(final String v) { this.model = v == null ? "" : v; }
    public Long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(final Long v) { this.timeoutSeconds = v; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(final int v) { this.maxTokens = v; }
    public double getTemperature() { return temperature; }
    public void setTemperature(final double v) { this.temperature = v; }
    public int getMaxContextItems() { return maxContextItems; }
    public void setMaxContextItems(final int v) { this.maxContextItems = v; }
    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(final int v) { this.maxContextChars = v; }
    public boolean isFallbackOnError() { return fallbackOnError; }
    public void setFallbackOnError(final boolean v) { this.fallbackOnError = v; }
}
