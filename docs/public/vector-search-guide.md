# Searchable vector search guide

This guide summarizes how to enable vector search and hybrid search in
Searchable, along with key tuning points.

## 1. How it works

- At document ingestion time, the `EmbeddingProvider` generates an
  embedding vector from the document and stores it in a Lucene HNSW
  index (`KnnFloatVectorField`, similarity: `DOT_PRODUCT`).
- At search time, the query string is vectorized by the same
  EmbeddingProvider and a k-NN search is executed.
- Hybrid search combines full-text search (BM25) with vector search.

## 2. Embedding providers

| Provider | Use case | Configuration |
| --- | --- | --- |
| `hash` (default) | Development and testing; deterministic but does not support semantic search | `searchable.embedding.dimension` |
| `onnx` (extension) | Production use; ONNX-format embedding model | `searchable.embedding.model-path` + Tokenizer Bean |

### Hash provider (default)

```properties
searchable.embedding.provider=hash
searchable.embedding.dimension=384
```

- 384-dimensional SHA-256-based deterministic vector.
- Semantic search does not work (identical text produces identical
  vectors; otherwise the output is pseudo-random).
- Useful for verifying the vector pipeline and for performance testing.

### Onnx provider (real model)

To use an ONNX-format embedding model, override the
`EmbeddingProvider` Bean in your Spring Configuration.

```java
@Configuration
public class MyEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingProvider onnxEmbedding(final SearchableProperties props) {
        final SearchableProperties.Embedding e = props.getEmbedding();
        final Tokenizer tokenizer = new MyTokenizer(e.getModelPath());  // 別途実装
        return new OnnxEmbeddingProvider(
            e.getModelId(),
            e.getModelPath(),
            tokenizer,
            e.getDimension(),
            e.getMaxSequenceLength()
        );
    }
}
```

`application.properties`:

```properties
searchable.embedding.provider=onnx
searchable.embedding.model-id=multilingual-e5-small
searchable.embedding.model-path=/path/to/multilingual-e5-small.onnx
searchable.embedding.dimension=384
searchable.embedding.max-sequence-length=512
```

**Note**: The Tokenizer must be implemented separately (a
model-specific tokenizer such as SentencePiece). Implement the
`com.searchable.core.domain.embedding.Tokenizer` interface and return
`{inputIds, attentionMask}` from `encode(text, maxLength)`.

### ONNX model distribution, retrieval, and cache strategy

Searchable does not bundle ONNX model files in the JAR.
`OnnxEmbeddingProvider` only accepts a `Path modelPath`; it neither
downloads nor caches the model, so users must place the file locally.

**Recommended models**:

| Model | Dimensions | Approx. size (before ONNX quantization) | Source |
| --- | --- | --- | --- |
| `intfloat/multilingual-e5-small` | 384 | ~140MB | <https://huggingface.co/intfloat/multilingual-e5-small> |
| `intfloat/multilingual-e5-base` | 768 | ~470MB | <https://huggingface.co/intfloat/multilingual-e5-base> |

**How to obtain** (a typical example from Hugging Face):

```bash
# huggingface-cli (recommended, no git lfs required)
pip install -U "huggingface_hub[cli]"
huggingface-cli download intfloat/multilingual-e5-small \
  --include "*.onnx" "tokenizer.json" "sentencepiece.bpe.model" \
  --local-dir ~/.cache/searchable/models/multilingual-e5-small
```

Using `git lfs`:

```bash
git lfs install
git clone https://huggingface.co/intfloat/multilingual-e5-small \
  ~/.cache/searchable/models/multilingual-e5-small
```

**Path convention**: The library does not enforce a specific path.
If you want to standardize within a team, start from
`~/.cache/searchable/models/<model-id>/` and set
`searchable.embedding.model-path` in `application.properties` to an
absolute path.

**Both the embedded (Java API) and examples paths**:

- When using the Java API directly, pass an already-resolved
  `OnnxEmbeddingProvider` via
  `SearchableLibrary.Builder.embeddingProvider(...)` from the
  application side.
- For the examples (REST API / webapp / MCP), set
  `searchable.embedding.model-path=/absolute/path/to/model.onnx` in
  `application.properties`. If the file is missing at startup, the
  application will fail to start (`IllegalStateException`).
- Whether to bundle the model file in a CI image or Docker image is a
  user decision. Do so only when including large binaries in CI
  artifacts is acceptable; otherwise, fetch it via an init container
  or entrypoint script.

**License check**: The multilingual-e5 family is MIT-licensed. When
using any other model, be sure to verify its license.

## 3. Namespace configuration

You can change the default behavior by setting `architecture` on the
Namespace.

| Value | Behavior |
| --- | --- |
| `FULL_TEXT` | Full-text search only |
| `VECTOR` | Vector search only |
| `HYBRID` | Combination of full-text and vector search |

### Namespace creation example

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "knowledge-base",
    "name": "Knowledge Base",
    "config": {
      "architecture": "HYBRID",
      "searchStrategy": "PARALLEL",
      "searchOrder": "FULL_TEXT_FIRST"
    }
  }'
```

### `searchStrategy` (the strategy in hybrid mode)

- `SEQUENTIAL`: The secondary engine reranks results from the primary
  engine (effectively an intersection). The order is determined by
  `searchOrder`.
  - `FULL_TEXT_FIRST`: Full-text search is primary.
  - `VECTOR_FIRST`: Vector search is primary.
- `PARALLEL`: Both engines run in parallel, and results are merged
  into a union ranking with Reciprocal Rank Fusion (RRF).

## 4. Search API

### Vector search

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "意味的に似た文書を探す",
    "namespaceIds": ["knowledge-base"],
    "searchType": "VECTOR",
    "options": { "maxResults": 10 }
  }'
```

### Hybrid search

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "全文検索 と 意味検索 の組み合わせ",
    "namespaceIds": ["knowledge-base"],
    "searchType": "HYBRID"
  }'
```

### Behavior when the type is omitted

If `searchType` is omitted, the `architecture` setting of the target
Namespace is used.

### Scope of section anchors (`SubResult`)

`SubResult` and `SubResult.anchorUrl` are **generated only for
full-text search**. Standalone vector search (`VECTOR`) results do not
include `subResults` (section splitting via `SubResult` has weak
semantics in vector space, so it is explicitly restricted to
full-text search). Hybrid search (`HYBRID`) results internally merge
full-text and vector hits, so documents found via full-text carry
`subResults` while documents found via vector do not.

## 5. Index construction

You can build a vector-enabled index through the usual endpoints
`/api/v1/index/documents` or `/api/v1/index/batch`. When the
`EmbeddingProvider` is enabled, vectors are computed and stored
automatically at ingestion time.

```bash
curl -X POST http://localhost:8080/api/v1/index/batch \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "knowledge-base",
    "documents": [
      {"id": "d1", "title": "...", "content": "..."},
      {"id": "d2", "title": "...", "content": "..."}
    ]
  }'
```

## 6. Performance characteristics

| Count | Search p99 | Index construction |
| --- | --- | --- |
| 100k | 1 ms (vector only) | ~88 seconds (including HNSW construction) |
| 100k | 1 ms (full-text only) | ~6 seconds |

> **Note on measurement units**: The p99 values above mean "less than
> 1 ms" because the legacy PoC code rounds `long` values in
> millisecond units. For the latest sub-millisecond figures, see the
> Performance section of the README (JMH 1.37, with warm/cold
> measurements reported separately).

### Reproducing the 88s/100k initial vector index build

To reproduce the "~88 seconds" above, the following must be aligned.

| Item | Value |
| --- | --- |
| Document count | 100,000 |
| Vector dimensions | 384 |
| Similarity function | `VectorSimilarityFunction.DOT_PRODUCT` |
| Storage | `MMapDirectory` (temporary directory) |
| Parallelism | Single-threaded (serial `addDocument` in a `for` loop) |
| `IndexWriterConfig.RAMBufferSizeMB` | 512 |
| HNSW parameters | Lucene defaults (M=16, efConstruction=100) |
| Analyzer | `JapaneseAnalyzer` (Kuromoji) |
| Embeddings | SHA-256-based hash (384 dimensions, L2-normalized) — **not dependent on a real model** |
| Hardware | macOS / Apple Silicon (as documented in the PoC README) |
| Java | 21 |
| Lucene | 10.4 (the legacy PoC used 10.2; the order of magnitude is the same) |
| Output | The third value of `[index] %,d docs (dim=%d) indexed in %,d ms` |

**Multiplier when using a real model**: Hash embeddings are orders of
magnitude faster than ONNX inference, so when you use
`OnnxEmbeddingProvider` + `multilingual-e5-small`, the total becomes
"embedding generation time + HNSW graph construction time," and on
Apple Silicon this typically stretches to 20–40 minutes for 100k
documents (CPU-bound). In production, combine the following to
shorten the ingestion time.

- Batch parallelism: parallelize with `parallelStream()` or
  `ExecutorService`.
- Batch size: `addDocument` in chunks of 1,000–10,000 documents and
  `commit` periodically.
- `RAMBufferSizeMB`: set to 256–1024 MB according to the JVM heap.

The implementation is not fixed on the library side; users control
this in the stage that precedes
`SearchableLibrary.Builder.embeddingProvider(...)`.

## 7. Tuning

- **Dimensions**: Higher dimensions improve accuracy but increase
  index size and search time. Representative values are
  multilingual-e5-small=384 and multilingual-e5-base=768.
- **HNSW parameters**: Lucene's default settings are sufficient.
  Extend `LuceneIndexProvider` if necessary to customize
  `IndexWriterConfig` (see the next section for details).
- **Batch size**: Increasing `RAMBufferSizeMB` at index construction
  time (e.g., from the default 64MB to 256MB) accelerates bulk
  ingestion.
- **Parallel hybrid**: Because a Virtual Thread executor is used, CPU
  and I/O overhead is low, but note that response time is the maximum
  latency of the two engines.

### HNSW parameter tuning guidance

Lucene's HNSW is implemented by `Lucene99HnswVectorsFormat`. The
build-time `M` / `efConstruction` and the search-time `topK` dominate
latency, recall, and index size.

| Parameter | Lucene default | Behavior | Increase to | Decrease to |
| --- | --- | --- | --- | --- |
| `M` | 16 | Maximum connections per node | Improve recall; larger index; longer build time | Shrink index; lower recall |
| `efConstruction` | 100 | Candidate search width during build | Improve recall; longer build time | Faster build; lower quality |
| `k` (search `topK`) | API-specified | Number of candidates at search time | Improve recall; higher search latency | Faster; lower recall |

> Lucene 10 does not provide an API for specifying search-time `ef`
> independently; `topK` serves directly as the starting point for the
> search width. When recall matters, take `topK` larger than the
> actual required count and post-rerank in the application.

**How to customize** (replace the part of a `LuceneIndexProvider`
subclass that builds the `IndexWriterConfig`):

```java
final var hnswFormat = new Lucene99HnswVectorsFormat(
    32,    // M (default 16) — リコール優先で 24〜32
    200    // efConstruction (default 100) — 構築時に余裕があれば 200〜400
);
final var codec = new Lucene101Codec() {
    @Override
    public KnnVectorsFormat getKnnVectorsFormatForField(final String field) {
        if ("vector".equals(field)) {
            return hnswFormat;
        }
        return super.getKnnVectorsFormatForField(field);
    }
};
indexWriterConfig.setCodec(codec);
```

**Tuning guidance** (at the 100k–1M scale):

- Start by measuring with Lucene's defaults (M=16, efConstruction=100).
- If recall feels insufficient, try **M=24** first. Going to M=32 or
  beyond bloats the index size by roughly 1.5–2x, so weigh that
  against your operational capacity.
- If you have build-time headroom, raise **efConstruction=200–400** to
  lift recall. Going from 100 to 400 makes the build time about 3–4x
  longer.
- When you need to trim search latency, lower `topK` on the API side
  and compensate with application-side filtering.

## 8. Troubleshooting

### "Vector search results are always the same"

- The Hash provider does not implement semantic search. Switch to the
  Onnx provider with a real model.

### "Vector search returns zero documents"

- Verify that the `EmbeddingProvider` was active when documents were
  ingested into the target Namespace.
- Documents ingested without a vector are not matched by KNN queries.
  Rebuild via `/api/v1/index/rebuild`.

### "Vector dimension mismatch error"

- Vectors of different dimensions cannot coexist in the same Namespace.
- To change dimensions, delete the Namespace and rebuild it.

---

**Document Version**: 1.1
**Last Updated**: 2026-06-07
**Status**: Phase 2 (reflects TASK-016 / TASK-017 / TASK-018)
