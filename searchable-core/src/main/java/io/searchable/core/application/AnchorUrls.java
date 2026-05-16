package io.searchable.core.application;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Helpers for deriving anchor URLs from section headings.
 *
 * <p>Used by {@code TASK-051} to expose deep-linkable anchors for
 * sub-result navigation. The output is a kebab-case slug with diacritics
 * stripped, suitable as the fragment portion of a URL
 * ({@code /docs/foo#installation}).
 */
public final class AnchorUrls {

    private AnchorUrls() { }

    /** Build an anchor (without the leading {@code #}) from a heading string. */
    public static String slugify(final String heading) {
        if (heading == null || heading.isBlank()) {
            return "";
        }
        final String normalized = Normalizer
            .normalize(heading, Normalizer.Form.NFKD)
            // Strip combining marks (accents) introduced by NFKD decomposition.
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase(Locale.ROOT);
        final StringBuilder sb = new StringBuilder(normalized.length());
        boolean lastDash = true;
        for (int i = 0; i < normalized.length(); i++) {
            final char c = normalized.charAt(i);
            if (isSlugChar(c)) {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        // Trim trailing dash.
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Combine a document-level URL with a heading slug.
     *
     * @param baseUrl document URL (may be {@code null} or empty when no base is known)
     * @param heading section heading; an empty/blank value yields {@code baseUrl}
     */
    public static String anchorFor(final String baseUrl, final String heading) {
        final String slug = slugify(heading);
        if (slug.isEmpty()) {
            return baseUrl;
        }
        final String base = baseUrl == null ? "" : baseUrl;
        return base + "#" + slug;
    }

    private static boolean isSlugChar(final char c) {
        // Latin alphanumerics + CJK + Hiragana + Katakana
        return (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9')
            || (c >= 0x3040 && c <= 0x309F) // Hiragana
            || (c >= 0x30A0 && c <= 0x30FF) // Katakana
            || (c >= 0x4E00 && c <= 0x9FFF); // CJK Unified Ideographs
    }
}
