package io.searchable.core.domain.namespace;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for {@link Namespace}.
 *
 * <p>Implementations live in the infrastructure layer
 * (e.g. {@code io.searchable.core.infrastructure.persistence.jdbc}).
 */
public interface NamespaceRepository {

    /** Insert a new namespace or replace an existing one with the same id. */
    void save(Namespace namespace);

    /** Find a namespace by its id, or {@link Optional#empty()} if missing. */
    Optional<Namespace> findById(String id);

    /** Return all namespaces ordered by id. */
    List<Namespace> findAll();

    /** Delete the namespace and return whether a row existed. */
    boolean delete(String id);

    /** Test whether a namespace with the given id exists. */
    boolean exists(String id);
}
