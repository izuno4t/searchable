package com.searchable.core.domain.parser;

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
     * @param source         document content
     * @param fallbackTitle  title to use when one cannot be extracted from {@code source}
     */
    ParsedDocument parse(String source, String fallbackTitle);
}
