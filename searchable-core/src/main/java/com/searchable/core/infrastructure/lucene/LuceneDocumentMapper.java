package com.searchable.core.infrastructure.lucene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.searchable.core.domain.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;

import java.util.Map;
import java.util.Objects;

/**
 * Converts between domain {@link Document} and Lucene's
 * {@link org.apache.lucene.document.Document}.
 */
public final class LuceneDocumentMapper {

    private final ObjectMapper objectMapper;

    public LuceneDocumentMapper() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public org.apache.lucene.document.Document toLucene(final Document doc) {
        Objects.requireNonNull(doc, "doc must not be null");
        final org.apache.lucene.document.Document lucene = new org.apache.lucene.document.Document();
        lucene.add(new StringField(LuceneFields.ID, doc.id(), Field.Store.YES));
        lucene.add(new StringField(LuceneFields.NAMESPACE_ID, doc.namespaceId(), Field.Store.YES));
        lucene.add(new Field(LuceneFields.TITLE, doc.title(),
            LuceneFields.ANALYZED_STORED_WITH_VECTORS));
        lucene.add(new Field(LuceneFields.CONTENT, doc.content(),
            LuceneFields.ANALYZED_STORED_WITH_VECTORS));
        lucene.add(new StoredField(LuceneFields.METADATA_JSON, serializeMetadata(doc.metadata())));
        if (doc.indexedAt() != null) {
            final long epoch = doc.indexedAt().toEpochMilli();
            lucene.add(new NumericDocValuesField(LuceneFields.INDEXED_AT_EPOCH, epoch));
            lucene.add(new StoredField(LuceneFields.INDEXED_AT_EPOCH, epoch));
        }
        return lucene;
    }

    public Map<String, Object> deserializeMetadata(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize document metadata", e);
        }
    }

    private String serializeMetadata(final Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize document metadata", e);
        }
    }
}
