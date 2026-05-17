package io.searchable.core.domain.dictionary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Closes the per-field validation branches in {@link UserDictionaryEntry}. */
class UserDictionaryEntryExtraTest {

    @Test
    void rejectsNullInEachField() {
        assertThatThrownBy(() -> new UserDictionaryEntry(null, "x", "y", "z"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("surface");
        assertThatThrownBy(() -> new UserDictionaryEntry("a", null, "y", "z"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("segmentation");
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", null, "z"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("reading");
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", "c", null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("pos");
    }

    @Test
    void rejectsBlankInEachField() {
        assertThatThrownBy(() -> new UserDictionaryEntry("a", " ", "c", "d"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", " ", "d"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", "c", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNewlineOrCarriageReturnInAnyField() {
        assertThatThrownBy(() -> new UserDictionaryEntry("a\n", "b", "c", "d"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b\r", "c", "d"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", "c\n", "d"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", "c", "d\r"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCommaInEachField() {
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b,c", "d", "e"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", "c,d", "e"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDictionaryEntry("a", "b", "c", "d,e"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromCsvRejectsNullLine() {
        assertThatThrownBy(() -> UserDictionaryEntry.fromCsv(null))
            .isInstanceOf(NullPointerException.class);
    }
}
