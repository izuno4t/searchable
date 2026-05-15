package com.searchable.core.domain.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Converts raw document text in a specific format into indexable
 * {@link ParsedDocument}.
 */
public interface DocumentParser {

    /** Short identifier for diagnostics (e.g. {@code plain}, {@code markdown}). */
    String name();

    /**
     * File extensions handled by this parser, lowercase with leading dot
     * (e.g. {@code .md}, {@code .markdown}).
     */
    List<String> supportedExtensions();

    /**
     * Parse the given source text.
     *
     * @param source         document content as text
     * @param fallbackTitle  title to use when one cannot be extracted from {@code source}
     */
    ParsedDocument parse(String source, String fallbackTitle);

    /**
     * Parse a stream of bytes. The default implementation reads the entire
     * stream as UTF-8 text and delegates to {@link #parse(String, String)};
     * binary formats (e.g. PDF) override this method.
     */
    default ParsedDocument parse(final InputStream stream,
                                 final String fallbackTitle) throws IOException {
        try (InputStream in = stream) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), fallbackTitle);
        }
    }
}
