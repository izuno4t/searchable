package io.searchable.admin.form;

import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Form backing the {@code POST /namespaces} screen.
 */
public class NamespaceCreateForm {

    @NotBlank
    @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,63}",
        message = "Use only lowercase letters, digits, hyphens, and underscores")
    private String id;

    @NotBlank
    @Size(max = 256)
    private String name;

    private SearchType architecture;
    private SearchStrategy searchStrategy;
    private SearchOrder searchOrder;

    public String getId() { return id; }
    public void setId(final String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(final String v) { this.name = v; }
    public SearchType getArchitecture() { return architecture; }
    public void setArchitecture(final SearchType v) { this.architecture = v; }
    public SearchStrategy getSearchStrategy() { return searchStrategy; }
    public void setSearchStrategy(final SearchStrategy v) { this.searchStrategy = v; }
    public SearchOrder getSearchOrder() { return searchOrder; }
    public void setSearchOrder(final SearchOrder v) { this.searchOrder = v; }

    public NamespaceConfigPatch toPatch() {
        return new NamespaceConfigPatch(architecture, searchStrategy, searchOrder,
            null, null, null);
    }
}
