package com.searchable.ui.form;

import com.searchable.core.domain.dictionary.UserDictionary;
import com.searchable.core.domain.dictionary.UserDictionaryEntry;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Form backing the dictionary edit page.
 *
 * <p>The {@code entriesCsv} text area accepts one entry per line in the
 * Kuromoji CSV format ({@code surface,segmentation,reading,pos}); empty
 * lines and lines starting with {@code #} are skipped.
 */
public class UserDictionaryForm {

    @NotBlank
    private String name;

    private String entriesCsv = "";

    public static UserDictionaryForm from(final UserDictionary dictionary) {
        final UserDictionaryForm form = new UserDictionaryForm();
        form.name = dictionary.name();
        final StringBuilder sb = new StringBuilder();
        for (final UserDictionaryEntry entry : dictionary.entries()) {
            sb.append(entry.toCsv()).append('\n');
        }
        form.entriesCsv = sb.toString();
        return form;
    }

    public String getName() { return name; }
    public void setName(final String v) { this.name = v; }
    public String getEntriesCsv() { return entriesCsv; }
    public void setEntriesCsv(final String v) { this.entriesCsv = v == null ? "" : v; }

    /**
     * Parse {@code entriesCsv} into validated entries.
     *
     * @return the parsed entries
     * @throws IllegalArgumentException on syntax / duplicate-surface errors
     */
    public List<UserDictionaryEntry> parseEntries() {
        final List<UserDictionaryEntry> entries = new ArrayList<>();
        final Set<String> surfaces = new HashSet<>();
        int lineNumber = 0;
        for (final String raw : entriesCsv.split("\\R")) {
            lineNumber++;
            final String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                final UserDictionaryEntry entry = UserDictionaryEntry.fromCsv(line);
                if (!surfaces.add(entry.surface())) {
                    throw new IllegalArgumentException(
                        "line " + lineNumber + ": duplicate surface form '"
                            + entry.surface() + "'");
                }
                entries.add(entry);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        return entries;
    }
}
