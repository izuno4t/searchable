package io.searchable.core.infrastructure.lucene;

/**
 * Storage backend used by {@link LuceneIndexProvider} to host each
 * namespace's Lucene index.
 */
public enum StorageBackend {

    /** Index files live on the local filesystem under {@code IndexLayout}. */
    FILESYSTEM,

    /**
     * Index lives in-process in a {@code ByteBuffersDirectory}.
     *
     * <p>Useful for tests and ephemeral workloads; data does not survive
     * JVM shutdown and is not compatible with read-only mode.
     */
    MEMORY
}
