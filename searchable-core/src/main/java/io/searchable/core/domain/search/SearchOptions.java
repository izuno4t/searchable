package io.searchable.core.domain.search;

/**
 * Optional per-request search behavior flags.
 *
 * @param highlightEnabled whether to include highlighted snippets in hits
 */
public record SearchOptions(boolean highlightEnabled) {

    public static SearchOptions defaults() {
        return new SearchOptions(true);
    }
}
