package com.searchable.core.domain.index;

/**
 * Lifecycle state of a namespace index.
 */
public enum IndexStatus {
    /** Index has not been created yet (no documents). */
    EMPTY,
    /** Index is being built or updated. */
    INDEXING,
    /** Index is healthy and serving searches. */
    READY,
    /** Index encountered an unrecoverable error. */
    ERROR
}
