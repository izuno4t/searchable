package io.searchable.admin.service;

import io.searchable.core.application.IndexService;
import io.searchable.core.domain.document.Document;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * Coverage for the {@link FileUploadService}'s null-original-filename
 * fallback branch (line 48 of the source). When a Spring {@link MultipartFile}
 * arrives with no filename header, the service should fall back to a
 * synthetic "upload" name. Since no parser is registered for that synthetic
 * name (no extension), the call ends with an IllegalArgumentException — that
 * is the path we assert here.
 */
class FileUploadServiceNullFilenameTest {

    @Test
    void nullFilenameTriggersUploadFallbackThenFailsOnUnknownParser() {
        final IndexService indexService = mock(IndexService.class);
        doNothing().when(indexService).index(any(Document.class));
        final FileUploadService service = new FileUploadService(indexService,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        final MultipartFile file = new NullNameMultipartFile("hello world".getBytes());

        // No filename → falls back to "upload" → no parser registered for it.
        assertThatThrownBy(() -> service.upload("ns", file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported file type")
            .hasMessageContaining("upload");
    }

    private static final class NullNameMultipartFile implements MultipartFile {
        private final byte[] data;
        NullNameMultipartFile(final byte[] data) { this.data = data; }
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return null; }
        @Override public String getContentType() { return null; }
        @Override public boolean isEmpty() { return data.length == 0; }
        @Override public long getSize() { return data.length; }
        @Override public byte[] getBytes() { return data; }
        @Override public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }
        @Override public void transferTo(final java.io.File dest) {
            throw new UnsupportedOperationException();
        }
    }
}
