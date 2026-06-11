package io.searchable.admin;

import io.searchable.admin.config.SearchableProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@code else if (X != null)} false branches inside
 * {@link SearchableProperties#normalizePaths()} that the existing tests
 * do not reach because {@link SearchableProperties.Index#getDirectory()}
 * and {@link SearchableProperties.Dictionary#getDirectory()} default to
 * non-null sentinels.
 */
class SearchablePropertiesNullSubpathTest {

    @TempDir Path tempDir;

    @Test
    void normalize_skipsIndexDirectoryWhenExplicitlyNull() {
        final SearchableProperties props = new SearchableProperties();
        props.setDataDirectory(tempDir);
        props.getIndex().setDirectory(null);
        props.getDictionary().setDirectory(null);

        props.normalizePaths();

        // Both setters were not invoked because the directories were null.
        assertThat(props.getIndex().getDirectory()).isNull();
        assertThat(props.getDictionary().getDirectory()).isNull();
    }
}
