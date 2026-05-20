package io.searchable.core.application;

import io.searchable.core.domain.document.DocumentMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Closes the validation / null branches in {@link DocumentBrowser}. */
class DocumentBrowserBranchTest {

    private DocumentBrowser browser;

    @BeforeEach
    void setUp() {
        browser = new DocumentBrowser(Mockito.mock(DocumentMetadataRepository.class));
    }

    @Test
    void rejectsNullRepository() {
        assertThatThrownBy(() -> new DocumentBrowser((DocumentMetadataRepository) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNamespace() {
        assertThatThrownBy(() -> browser.list(null, 0, 10))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> browser.list("ns", 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> browser.list("ns", 0, -3))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
