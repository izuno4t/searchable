package com.searchable.core.domain.dictionary;

import java.util.Objects;

/**
 * Single entry of a Kuromoji user dictionary.
 *
 * <p>Maps to one CSV line in the format consumed by
 * {@code org.apache.lucene.analysis.ja.dict.UserDictionary}:
 * {@code surface,segmentation,reading,pos}.
 *
 * @param surface      表層形 (form actually written in source text)
 * @param segmentation 分かち書きされた単語列（空白区切り）
 * @param reading      読み（カタカナ、空白区切り）
 * @param pos          品詞ラベル（任意の文字列）
 */
public record UserDictionaryEntry(
    String surface,
    String segmentation,
    String reading,
    String pos
) {

    public UserDictionaryEntry {
        Objects.requireNonNull(surface, "surface must not be null");
        Objects.requireNonNull(segmentation, "segmentation must not be null");
        Objects.requireNonNull(reading, "reading must not be null");
        Objects.requireNonNull(pos, "pos must not be null");
        if (surface.isBlank() || segmentation.isBlank() || reading.isBlank() || pos.isBlank()) {
            throw new IllegalArgumentException("all fields are required");
        }
        if (containsCommaOrNewline(surface) || containsCommaOrNewline(segmentation)
            || containsCommaOrNewline(reading) || containsCommaOrNewline(pos)) {
            throw new IllegalArgumentException(
                "fields must not contain commas or line breaks");
        }
    }

    /** Render the entry in Kuromoji's CSV format. */
    public String toCsv() {
        return surface + "," + segmentation + "," + reading + "," + pos;
    }

    public static UserDictionaryEntry fromCsv(final String line) {
        Objects.requireNonNull(line, "line must not be null");
        final String[] parts = line.split(",", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                "Expected 4 comma-separated fields, got " + parts.length + ": " + line);
        }
        return new UserDictionaryEntry(parts[0].trim(), parts[1].trim(),
            parts[2].trim(), parts[3].trim());
    }

    private static boolean containsCommaOrNewline(final String s) {
        return s.indexOf(',') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
    }
}
