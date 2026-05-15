package com.searchable.core.infrastructure.lucene;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;

/**
 * Field type constants used by the Lucene mapping layer.
 */
public final class LuceneFields {

    /** Stored, indexed, analyzed text with term vectors for highlighting. */
    public static final FieldType ANALYZED_STORED_WITH_VECTORS;

    public static final String ID = "id";
    public static final String NAMESPACE_ID = "namespaceId";
    public static final String TITLE = "title";
    public static final String CONTENT = "content";
    public static final String METADATA_JSON = "metadataJson";
    public static final String INDEXED_AT_EPOCH = "indexedAtEpoch";

    static {
        final FieldType type = new FieldType();
        type.setStored(true);
        type.setTokenized(true);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        type.setStoreTermVectors(true);
        type.setStoreTermVectorPositions(true);
        type.setStoreTermVectorOffsets(true);
        type.freeze();
        ANALYZED_STORED_WITH_VECTORS = type;
    }

    private LuceneFields() { }
}
