-- Searchable metadata schema (H2 / PostgreSQL-compatible standard SQL).
--
-- Five tables capture everything the Searchable runtime needs outside
-- the Lucene index directory itself:
--
--   * NAMESPACE          -- multi-tenant logical indexes + their config
--   * INDEX_METADATA     -- per-namespace statistics and lifecycle state
--   * DOCUMENT_SOURCE    -- per-document provenance for change detection
--   * DOCUMENT_METADATA  -- authoritative per-document attribute registry
--   * USER_DICTIONARY    -- analyzer user dictionaries (global + namespace)
--
-- NAMESPACE is the root; every other table FKs back to it with
-- ON DELETE CASCADE so deleting a namespace tears down all of its rows.

-- Multi-tenant root. One row per logical index ("namespace") that the
-- application exposes. CONFIG_JSON holds the full serialized
-- NamespaceConfig (architecture FULL_TEXT/VECTOR/HYBRID, search
-- strategy, embedding model, analyzer settings, ...). CREATED_AT /
-- UPDATED_AT track the lifecycle of the namespace itself, not the
-- documents it contains (those live in INDEX_METADATA).
CREATE TABLE IF NOT EXISTS NAMESPACE (
    ID            VARCHAR(64) PRIMARY KEY,
    NAME          VARCHAR(256) NOT NULL,
    CONFIG_JSON   CLOB NOT NULL,
    CREATED_AT    TIMESTAMP WITH TIME ZONE NOT NULL,
    UPDATED_AT    TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Per-namespace index dashboard. Caches aggregate counts and on-disk
-- size, and exposes the lifecycle state of each namespace's Lucene
-- directory via STATUS (EMPTY / INDEXING / READY / ERROR; see
-- io.searchable.core.domain.index.IndexStatus). One-to-one with
-- NAMESPACE -- the PK is NAMESPACE_ID. STATISTICS_JSON is a freeform
-- map for engine-specific extras that do not warrant a dedicated
-- column (e.g. segment counts, vector counts).
CREATE TABLE IF NOT EXISTS INDEX_METADATA (
    NAMESPACE_ID     VARCHAR(64) PRIMARY KEY,
    DOCUMENT_COUNT   BIGINT NOT NULL DEFAULT 0,
    INDEX_SIZE_BYTES BIGINT NOT NULL DEFAULT 0,
    STATUS           VARCHAR(32) NOT NULL,
    LAST_UPDATED     TIMESTAMP WITH TIME ZONE NOT NULL,
    STATISTICS_JSON  CLOB,
    CONSTRAINT FK_INDEX_METADATA_NAMESPACE
        FOREIGN KEY (NAMESPACE_ID) REFERENCES NAMESPACE (ID) ON DELETE CASCADE
);

-- Change-detection registry. Records WHERE each indexed document came
-- from (SOURCE_TYPE + SOURCE_LOCATION, e.g. "file" + an absolute path,
-- or "plugin:my-plugin" + a plugin-defined locator), the CONTENT_HASH
-- captured at ingest time, and the source-side mtime. A re-ingest
-- compares these values against the live source and skips documents
-- whose hash/mtime has not changed.
--
-- Responsibility is *provenance only*. The document payload (tokens,
-- vectors) lives in the Lucene index; the document's user-facing
-- attributes (title, metadata) live in DOCUMENT_METADATA.
CREATE TABLE IF NOT EXISTS DOCUMENT_SOURCE (
    NAMESPACE_ID     VARCHAR(64)  NOT NULL,
    DOCUMENT_ID      VARCHAR(128) NOT NULL,
    SOURCE_TYPE      VARCHAR(64)  NOT NULL,
    SOURCE_LOCATION  VARCHAR(2048) NOT NULL,
    CONTENT_HASH     VARCHAR(128),
    SOURCE_UPDATED   TIMESTAMP WITH TIME ZONE,
    INDEXED_AT       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (NAMESPACE_ID, DOCUMENT_ID),
    CONSTRAINT FK_DOCUMENT_SOURCE_NAMESPACE
        FOREIGN KEY (NAMESPACE_ID) REFERENCES NAMESPACE (ID) ON DELETE CASCADE
);

-- Authoritative per-document registry. Holds the document-level
-- attributes (TITLE, free-form METADATA_JSON, INDEXED_AT) that used to
-- live as Lucene stored fields on every chunk. Pulling them out keeps
-- the Lucene index focused on search-time data (tokens, vectors,
-- chunk-specific fields) and lets document listing / facet
-- aggregation be answered with plain SQL.
--
-- Keyed by the natural composite (NAMESPACE_ID, DOCUMENT_ID) -- no
-- surrogate id. See docs/architecture.md §5.7 and
-- io.searchable.core.domain.document.DocumentMetadataRecord. The
-- supporting index serves the "newest documents in a namespace"
-- listing query used by the admin UI and REST API.
CREATE TABLE IF NOT EXISTS DOCUMENT_METADATA (
    NAMESPACE_ID     VARCHAR(64)  NOT NULL,
    DOCUMENT_ID      VARCHAR(128) NOT NULL,
    TITLE            VARCHAR(1024) NOT NULL,
    METADATA_JSON    CLOB,
    INDEXED_AT       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (NAMESPACE_ID, DOCUMENT_ID),
    CONSTRAINT FK_DOCUMENT_METADATA_NAMESPACE
        FOREIGN KEY (NAMESPACE_ID) REFERENCES NAMESPACE (ID) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS IDX_DOCUMENT_METADATA_LISTING
    ON DOCUMENT_METADATA (NAMESPACE_ID, INDEXED_AT DESC);

-- Analyzer user-dictionary store (Kuromoji / Sudachi). One row per
-- scope; SCOPE_KEY is either the literal "GLOBAL" or
-- "NAMESPACE:<namespaceId>" (see DictionaryScope). GLOBAL entries
-- apply to every namespace; namespace-scoped entries layer additional
-- terms on top of GLOBAL. ENTRIES_CSV stores the dictionary as a
-- single CSV blob (one entry per line, analyzer-specific columns) so
-- the row can be reloaded into the analyzer without further joins.
--
-- This table is deliberately *not* keyed by NAMESPACE_ID; the
-- GLOBAL row has no namespace and namespace-scoped rows embed the id
-- in SCOPE_KEY. As a consequence, deleting a NAMESPACE row does not
-- cascade to namespace-scoped dictionary rows -- callers must purge
-- them explicitly when removing a namespace.
CREATE TABLE IF NOT EXISTS USER_DICTIONARY (
    SCOPE_KEY    VARCHAR(128) PRIMARY KEY,
    NAME         VARCHAR(256) NOT NULL,
    ENTRIES_CSV  CLOB,
    UPDATED_AT   TIMESTAMP WITH TIME ZONE NOT NULL
);
