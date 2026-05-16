package io.searchable.admin.form;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import jakarta.validation.constraints.NotNull;

/**
 * Form backing {@code POST /settings}.
 */
public class GlobalConfigForm {

    @NotNull
    private SearchType defaultArchitecture;

    @NotNull
    private SearchStrategy defaultSearchStrategy;

    @NotNull
    private SearchOrder defaultSearchOrder;

    public static GlobalConfigForm from(final GlobalConfig config) {
        final GlobalConfigForm form = new GlobalConfigForm();
        form.defaultArchitecture = config.defaultArchitecture();
        form.defaultSearchStrategy = config.defaultSearchStrategy();
        form.defaultSearchOrder = config.defaultSearchOrder();
        return form;
    }

    public GlobalConfig toGlobalConfig() {
        return new GlobalConfig(defaultArchitecture, defaultSearchStrategy, defaultSearchOrder);
    }

    public SearchType getDefaultArchitecture() { return defaultArchitecture; }
    public void setDefaultArchitecture(final SearchType v) { this.defaultArchitecture = v; }
    public SearchStrategy getDefaultSearchStrategy() { return defaultSearchStrategy; }
    public void setDefaultSearchStrategy(final SearchStrategy v) { this.defaultSearchStrategy = v; }
    public SearchOrder getDefaultSearchOrder() { return defaultSearchOrder; }
    public void setDefaultSearchOrder(final SearchOrder v) { this.defaultSearchOrder = v; }
}
