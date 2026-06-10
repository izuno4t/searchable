# Searchable chunking strategy guide

How to choose a splitting (chunking) strategy when vectorizing and
indexing documents.

## 1. How it works

- When a document is registered into a Namespace, `LuceneIndexer`
  splits the document into one or more chunks via `ChunkingStrategy`.
- Each chunk is indexed as an independent Lucene sub-document (with
  `parentId` and `chunkOrdinal`).
- Vectors are generated and stored per chunk.
- By aggregating on the parent ID, search results can be handled at
  either the document level or the chunk level (current state: the
  search API returns the parent ID; deduplication is handled by the
  caller).

## 2. Built-in strategies

| Strategy | Default | Description | Primary use |
| --- | --- | --- | --- |
| `whole` | Yes | One document = one chunk (title + body) | Default. Short text, compatibility-focused |
| `fixed` | | Split by character count with `overlap` | Long text, working around the model's `max_tokens` limit |
| `sentence` | | Split on sentence-ending punctuation (`。!?.!?`) and pack to the target size | General Japanese documents |
| `paragraph` | | Split on blank lines | Documents where paragraphs are self-contained |
| `section` | | Split by the headings returned by the parser (requires Markdown/HTML, etc.) | Structured documents |

## 3. Configuration

### `application.properties`

```properties
# Default is "whole"
searchable.chunking.strategy=fixed
searchable.chunking.chunk-size=512
searchable.chunking.overlap=64
# Target size for the sentence strategy
searchable.chunking.sentence-target-size=400
```

### Supported strategy values

`whole` / `fixed` / `sentence` / `paragraph` / `section`

### Per-Namespace overrides

Following the scope hierarchy in requirement 2.1.2, global
configuration plus per-Namespace overrides (per-Namespace overrides
are not yet publicly exposed; they will be added incrementally under
the Phase 4 tasks). If you need this immediately, override the Spring
Bean at application startup.

## 4. How to choose a strategy

```text
Document length < 1,000 chars     → whole
Medium (1,000-10,000)             → sentence or paragraph
Long (10,000+)                    → fixed (chunkSize=512, overlap=64)
Has heading structure             → section
```

### Trade-offs per strategy

#### whole (default)

- Pro: maximum compatibility, one document = one vector
- Con: long text is truncated at the embedding model's `max_tokens`
- Con: cannot identify which part of the document was hit

#### fixed

- Pro: reliably fits within size limits
- Pro: `overlap` prevents losses across chunk boundaries
- Con: may cut in the middle of a sentence or paragraph

#### sentence

- Pro: does not cut in the middle of a sentence
- Pro: packs efficiently up to the target size
- Con: does not work well for documents without sentence-ending
  punctuation (`。!?`)

#### paragraph

- Pro: ideal for documents whose meaning closes at blank lines
- Con: unbalanced when paragraphs are extremely long or short

#### section

- Pro: semantically correct splits for structured documents
  (Markdown/AsciiDoc/HTML)
- Pro: heading text is also vectorized, improving search accuracy
- Con: falls back to `whole` for unstructured documents

## 5. Verifying the behavior

### Upload with whole (default)

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "demo",
    "document": {
      "id": "d1",
      "title": "Searchable",
      "content": "長文の本文..."
    }
  }'

curl http://localhost:8080/api/v1/index/demo/metadata
# → documentCount: 1
```

### Switch to fixed and re-ingest the same long text

`application.properties`:

```properties
searchable.chunking.strategy=fixed
searchable.chunking.chunk-size=200
searchable.chunking.overlap=30
```

After restart:

```bash
curl -X POST http://localhost:8080/api/v1/index/documents ...
curl http://localhost:8080/api/v1/index/demo/metadata
# → documentCount: N (number of chunks)
```

Searches are aggregated by parent ID, and `SearchHit.documentId()`
returns the original document ID (`"d1"`).

## 6. Impact on existing indexes

Changing the strategy does **not re-split documents that are already
indexed**. To apply the new strategy across the board:

```bash
curl -X POST http://localhost:8080/api/v1/index/rebuild \
  -H 'Content-Type: application/json' \
  -d '{"namespaceId": "demo"}'
```

…and then re-upload the documents (rebuild writes a new empty index
directory, then switches over by atomically renaming the directory on
completion; the old version is deleted after a 30-second grace period.
Searches are not interrupted. Re-ingestion from the source is done by
a plugin or manually).

## 7. Performance characteristics

- **Indexing time**: proportional to the number of chunks (more
  embedding computations are required).
- **Search latency**: due to HNSW properties, latency grows
  logarithmically even as the number of sub-documents grows (TASK-123
  measured a max of 1 ms at 100,000 entries / dim=384).
- **Index size**: grows with the number of chunks (vector dimension ×
  chunk count).

## 8. Implementing a custom strategy programmatically

Implement the `ChunkingStrategy` interface and replace the Bean:

```java
public final class MyCustomChunking implements ChunkingStrategy {
    @Override public String name() { return "custom"; }
    @Override public List<Chunk> chunk(Document document) {
        // ...
    }
}

@Configuration
public class CustomChunkingConfig {
    @Bean @Primary
    public ChunkingStrategy chunkingStrategy() {
        return new MyCustomChunking();
    }
}
```

## 9. Troubleshooting

### Chunks are not split as expected

- Check the `chunk-size` and `overlap` values in
  `application.properties`.
- `section` is not working → verify that the document's
  `metadata.format` is a structured format such as Markdown or HTML.
- `sentence` is not working → verify that sentence-ending punctuation
  (`。！？.!?`) is present.

### The meaning of `documentCount` changes

- whole: 1 document = 1 (as before).
- Other strategies: 1 document = N (the number of chunks).
- If you need an accurate document count, you need to take the
  distinct count of `parentId` as an alternative to
  `searchable.documentCount` (planned for Phase 4 extension).

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 1 additional feature
