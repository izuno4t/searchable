package com.searchable.api.controller.payload;

import com.searchable.core.domain.dictionary.UserDictionaryEntry;
import jakarta.validation.constraints.NotBlank;

/** Wire form for one user-dictionary entry (Kuromoji CSV row). */
public record DictionaryEntryPayload(
    @NotBlank String surface,
    @NotBlank String segmentation,
    @NotBlank String reading,
    @NotBlank String pos
) {

    public static DictionaryEntryPayload from(final UserDictionaryEntry entry) {
        return new DictionaryEntryPayload(entry.surface(), entry.segmentation(),
            entry.reading(), entry.pos());
    }

    public UserDictionaryEntry toDomain() {
        return new UserDictionaryEntry(surface, segmentation, reading, pos);
    }
}
