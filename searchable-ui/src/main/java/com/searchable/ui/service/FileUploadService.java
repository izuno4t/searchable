package com.searchable.ui.service;

import com.searchable.core.application.IndexService;
import com.searchable.core.domain.document.Document;
import com.searchable.core.domain.parser.DocumentParser;
import com.searchable.core.domain.parser.ParsedDocument;
import com.searchable.core.infrastructure.parser.ParserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles uploaded files by selecting the appropriate parser and feeding
 * the parsed document into {@link IndexService}.
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final IndexService indexService;
    private final ParserRegistry parsers;
    private final Clock clock;

    public FileUploadService(final IndexService indexService, final Clock clock) {
        this.indexService = Objects.requireNonNull(indexService);
        this.parsers = ParserRegistry.defaults();
        this.clock = Objects.requireNonNull(clock);
    }

    /** Index a single uploaded file under the given namespace. */
    public UploadResult upload(final String namespaceId, final MultipartFile file) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(file, "file must not be null");
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        final String fileName = file.getOriginalFilename() == null
            ? "upload" : file.getOriginalFilename();
        final DocumentParser parser = parsers.resolveForFile(fileName)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported file type: " + fileName));

        final ParsedDocument parsed;
        try {
            parsed = parser.parse(file.getInputStream(), fileName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file " + fileName, e);
        }

        final String documentId = fileName.replaceAll("[^A-Za-z0-9._-]", "_")
            + "-" + UUID.randomUUID();
        final Map<String, Object> metadata = new HashMap<>(parsed.metadata());
        metadata.put("originalFileName", fileName);
        metadata.put("contentSize", file.getSize());

        final Document document = Document.builder()
            .id(documentId)
            .namespaceId(namespaceId)
            .title(parsed.title())
            .content(parsed.content())
            .metadata(metadata)
            .indexedAt(clock.instant())
            .build();
        indexService.index(document);
        log.info("uploaded {} ({} bytes, parser={}) into namespace {}",
            fileName, file.getSize(), parser.name(), namespaceId);
        return new UploadResult(documentId, fileName, parser.name(),
            new String(("indexed (" + (file.getSize()) + " bytes)").getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));
    }

    public record UploadResult(String documentId, String fileName, String parserName,
                               String status) { }
}
