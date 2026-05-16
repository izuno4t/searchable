package io.searchable.core.infrastructure.lucene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.VectorSimilarityFunction;

import java.util.Map;
import java.util.Objects;

/**
 * Converts a domain {@link Document} (optionally split into one or more
 * {@link Chunk}s) into Lucene's {@link org.apache.lucene.document.Document}.
 *
 * <p>Lucene-level fields:
 * <ul>
 *   <li>{@code id} = chunk id (e.g. {@code doc-1#0})</li>
 *   <li>{@code parentId} = original document id</li>
 *   <li>{@code chunkOrdinal} = position of the chunk within the parent</li>
 *   <li>{@code title}, {@code content} = stored & analyzed</li>
 *   <li>{@code vector} = KNN float vector (when an embedding is provided)</li>
 * </ul>
 */
public final class LuceneDocumentMapper {

    private final ObjectMapper objectMapper;

    public LuceneDocumentMapper() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Backwards-compat overload — treats the whole document as a single chunk. */
    public org.apache.lucene.document.Document toLucene(final Document doc) {
        return toLucene(doc, null);
    }

    /** Backwards-compat overload — single chunk with a vector. */
    public org.apache.lucene.document.Document toLucene(final Document doc, final float[] vector) {
        Objects.requireNonNull(doc, "doc must not be null");
        final Chunk whole = new Chunk(doc.id(), 0, Chunk.defaultChunkId(doc.id(), 0),
            doc.title() + "\n" + doc.content(), Map.of("strategy", "whole"));
        return toLucene(doc, whole, vector);
    }

    /**
     * Build a Lucene sub-document for one chunk of the given document.
     *
     * @param doc    parent domain document
     * @param chunk  chunk produced by a {@code ChunkingStrategy}
     * @param vector L2-normalized embedding vector for the chunk
     *               (or {@code null} to skip)
     */
    public org.apache.lucene.document.Document toLucene(final Document doc,
                                                        final Chunk chunk,
                                                        final float[] vector) {
        Objects.requireNonNull(doc, "doc must not be null");
        Objects.requireNonNull(chunk, "chunk must not be null");
        final org.apache.lucene.document.Document lucene = new org.apache.lucene.document.Document();
        lucene.add(new StringField(LuceneFields.ID, chunk.chunkId(), Field.Store.YES));
        lucene.add(new StringField(LuceneFields.PARENT_ID, doc.id(), Field.Store.YES));
        lucene.add(new StoredField(LuceneFields.CHUNK_ORDINAL, chunk.ordinal()));
        lucene.add(new NumericDocValuesField(LuceneFields.CHUNK_ORDINAL, chunk.ordinal()));
        lucene.add(new StringField(LuceneFields.NAMESPACE_ID, doc.namespaceId(), Field.Store.YES));
        lucene.add(new Field(LuceneFields.TITLE, doc.title(),
            LuceneFields.ANALYZED_STORED_WITH_VECTORS));
        lucene.add(new Field(LuceneFields.CONTENT, chunk.text(),
            LuceneFields.ANALYZED_STORED_WITH_VECTORS));
        lucene.add(new StoredField(LuceneFields.METADATA_JSON, serializeMetadata(doc.metadata())));
        lucene.add(new StoredField(LuceneFields.CHUNK_METADATA_JSON,
            serializeMetadata(chunk.metadata())));
        if (doc.indexedAt() != null) {
            final long epoch = doc.indexedAt().toEpochMilli();
            lucene.add(new NumericDocValuesField(LuceneFields.INDEXED_AT_EPOCH, epoch));
            lucene.add(new StoredField(LuceneFields.INDEXED_AT_EPOCH, epoch));
        }
        if (vector != null) {
            lucene.add(new KnnFloatVectorField(LuceneFields.VECTOR, vector,
                VectorSimilarityFunction.DOT_PRODUCT));
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
