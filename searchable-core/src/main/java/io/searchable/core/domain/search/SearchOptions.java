package io.searchable.core.domain.search;

/**
 * Optional per-request search behavior flags.
 *
 * @param highlightEnabled whether to include highlighted snippets in hits
 * @param snippetLength    maximum number of characters per highlighted snippet
 *                         (positive). The default {@value #DEFAULT_SNIPPET_LENGTH}
 *                         matches what most search UIs render in two lines.
 * @param escapeMarkup     when {@code true}, HTML special characters in the
 *                         original document are escaped in highlighted snippets
 *                         so the inserted {@code <mark>} tags remain the only
 *                         active markup. Set to {@code false} when the caller
 *                         already trusts the indexed content (e.g. internal
 *                         knowledge base) and wants to preserve formatting.
 * @param lazyLoad         when {@code true}, the search engine returns the
 *                         smallest payload possible (document id, namespace,
 *                         title, score, metadata) and omits the full
 *                         {@code content} field and highlight fragments.
 *                         Callers should fetch the body on demand from
 *                         {@code DocumentBrowser} / the source store.
 * @param bm25K1           BM25 {@code k1} term-frequency saturation override
 *                         ({@code null} keeps the Lucene default of 1.2).
 *                         Per-request overrides take priority over any
 *                         namespace-level value.
 * @param bm25B            BM25 {@code b} length-normalization override
 *                         ({@code null} keeps the Lucene default of 0.75).
 */
public record SearchOptions(
    boolean highlightEnabled,
    int snippetLength,
    boolean escapeMarkup,
    boolean lazyLoad,
    Double bm25K1,
    Double bm25B
) {

    public static final int DEFAULT_SNIPPET_LENGTH = 200;

    public SearchOptions {
        if (snippetLength <= 0) {
            snippetLength = DEFAULT_SNIPPET_LENGTH;
        }
        if (bm25K1 != null && (bm25K1 < 0.0 || Double.isNaN(bm25K1) || Double.isInfinite(bm25K1))) {
            throw new IllegalArgumentException(
                "bm25K1 must be a finite non-negative number, was " + bm25K1);
        }
        if (bm25B != null && (bm25B < 0.0 || bm25B > 1.0 || Double.isNaN(bm25B))) {
            throw new IllegalArgumentException(
                "bm25B must be in [0.0, 1.0], was " + bm25B);
        }
    }

    /** Backward-compatible constructor matching the original single-arg form. */
    public SearchOptions(final boolean highlightEnabled) {
        this(highlightEnabled, DEFAULT_SNIPPET_LENGTH, true, false, null, null);
    }

    /** Backward-compatible constructor without {@code lazyLoad} / BM25 overrides. */
    public SearchOptions(final boolean highlightEnabled,
                         final int snippetLength,
                         final boolean escapeMarkup) {
        this(highlightEnabled, snippetLength, escapeMarkup, false, null, null);
    }

    /** Backward-compatible constructor without BM25 overrides. */
    public SearchOptions(final boolean highlightEnabled,
                         final int snippetLength,
                         final boolean escapeMarkup,
                         final boolean lazyLoad) {
        this(highlightEnabled, snippetLength, escapeMarkup, lazyLoad, null, null);
    }

    public static SearchOptions defaults() {
        return new SearchOptions(true, DEFAULT_SNIPPET_LENGTH, true, false, null, null);
    }
}
