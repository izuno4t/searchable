package io.searchable.core.domain.document;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A document indexed under a namespace.
 *
 * <p>Construction is performed via {@link #builder()} to keep call-sites readable
 * when only a subset of fields is needed.
 */
public final class Document {

    private final String id;
    private final String namespaceId;
    private final String title;
    private final String content;
    private final Map<String, Object> metadata;
    private final DocumentSource source;
    private final Instant indexedAt;

    private Document(final Builder b) {
        this.id = Objects.requireNonNull(b.id, "id must not be null");
        this.namespaceId = Objects.requireNonNull(b.namespaceId, "namespaceId must not be null");
        this.title = Objects.requireNonNull(b.title, "title must not be null");
        this.content = Objects.requireNonNull(b.content, "content must not be null");
        this.metadata = b.metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(b.metadata));
        this.source = b.source;
        this.indexedAt = b.indexedAt;
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (namespaceId.isBlank()) {
            throw new IllegalArgumentException("namespaceId must not be blank");
        }
    }

    public String id() { return id; }
    public String namespaceId() { return namespaceId; }
    public String title() { return title; }
    public String content() { return content; }
    public Map<String, Object> metadata() { return metadata; }
    public DocumentSource source() { return source; }
    public Instant indexedAt() { return indexedAt; }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id).namespaceId(namespaceId)
            .title(title).content(content)
            .metadata(metadata)
            .source(source)
            .indexedAt(indexedAt);
    }

    /** Builder for {@link Document}. */
    public static final class Builder {
        private String id;
        private String namespaceId;
        private String title;
        private String content;
        private Map<String, Object> metadata;
        private DocumentSource source;
        private Instant indexedAt;

        public Builder id(final String value) { this.id = value; return this; }
        public Builder namespaceId(final String value) { this.namespaceId = value; return this; }
        public Builder title(final String value) { this.title = value; return this; }
        public Builder content(final String value) { this.content = value; return this; }
        public Builder metadata(final Map<String, Object> value) { this.metadata = value; return this; }
        public Builder source(final DocumentSource value) { this.source = value; return this; }
        public Builder indexedAt(final Instant value) { this.indexedAt = value; return this; }

        public Document build() {
            return new Document(this);
        }
    }
}
