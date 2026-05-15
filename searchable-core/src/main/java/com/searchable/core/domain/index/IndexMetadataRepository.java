package com.searchable.core.domain.index;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for {@link IndexMetadata}.
 */
public interface IndexMetadataRepository {

    /** Insert or replace metadata for a namespace. */
    void save(IndexMetadata metadata);

    /** Load metadata for a namespace, if it exists. */
    Optional<IndexMetadata> findByNamespaceId(String namespaceId);

    /** Load metadata for every known namespace. */
    List<IndexMetadata> findAll();

    /** Delete metadata for a namespace. */
    boolean delete(String namespaceId);
}
