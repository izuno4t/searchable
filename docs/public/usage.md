# Searchable - Usage Guide

A reference for day-to-day use of Searchable.
For initial setup, see [getting-started.md](getting-started.md);
for the exhaustive API specification, see [examples/api/api-specification.md](../examples/api/api-specification.md).

## 1. Choosing a usage form

Searchable provides the same core functionality in three forms.

| Form | When to use |
| --- | --- |
| **Java library** | Embed into an existing Java application / avoid network overhead |
| **REST API server** | Use from multiple clients regardless of language / extract as a microservice |
| **MCP server** | Call as a tool from AI clients such as Claude Desktop |

Multiple forms can be used simultaneously. For example, a common setup
uses REST for the search service and MCP for AI clients.

## 2. Using as a Java library

### 2.1 Initialization

```java
SearchableLibrary library = SearchableLibrary.builder()
    .configPath("/path/to/config.yaml")    // Configure from a file
    .build();

// Or directly from code
SearchableLibrary library = SearchableLibrary.builder()
    .dataDirectory("/path/to/data")
    .persistenceType(PersistenceType.H2)
    .globalConfig(SearchableGlobalConfig.builder()
        .defaultSearchArchitecture(SearchArchitecture.HYBRID)
        .build())
    .build();

library.start();
```

After use, call `library.shutdown()` to release resources.

### 2.2 Namespace operations

```java
NamespaceService namespaceService = library.getNamespaceService();

Namespace namespace = namespaceService.createNamespace(
    NamespaceCreateRequest.builder()
        .id("project-a")
        .name("Project A")
        .config(NamespaceConfig.builder()
            .architecture(SearchArchitecture.HYBRID)
            .searchStrategy(SearchStrategy.PARALLEL)
            .build())
        .build());
```

### 2.3 Index registration

```java
IndexService indexService = library.getIndexService();

Document document = Document.builder()
    .id("doc-1")
    .title("製品マニュアル")
    .content("Searchable は日本語形態素解析に対応した全文検索ライブラリです。")
    .metadata(Map.of(
        "url", Path.of("/srv/docs/manual.md").toUri().toString(),  // 推奨: URI で origin を記録
        "category", "product",
        "lang", "ja"))
    .build();

indexService.indexDocument("project-a", document);
```

For batch registration, use `indexDocuments(namespaceId, List<Document>)`.

#### Reserved keys in `Document.metadata`

`metadata` is a free-form field, but several keys carry special meaning
in the library and the sample UI.

| Key | Value | Purpose |
| --- | --- | --- |
| `url` | **URI** (RFC 3986), scheme required | Origin reference of the document. `file:///abs/path`, `http(s)://...`, `ftp://...`, `s3://bucket/key`, etc. Raw paths (`/abs/path`) are not allowed |
| `contentType` | **MIME type** | Source format of the document. `text/plain` / `text/markdown` / `text/html` / `text/asciidoc` / `application/pdf`, etc. Office MIME types are also supported. See [architecture.md §5.7](architecture.md) |
| `category` | string | For facets |
| `lang` | string | For facets |
| `tags` | string or string[] | For facets |

If `metadata.url` is set, a direct link to the original document can be
generated from the search result (`SearchHit.metadata.url`), and for
section-level hits (`SubResult`), `anchorUrl = url + "#heading-slug"` is
assembled automatically.

Note that `SubResult` is **generated only by full-text search**. For
vector-only search and for "documents that hit via vector", `subResults`
is an empty array and `anchorUrl` is absent. Build the UI so it works
even when `subResults` is empty (a direct link to the original document
can be built from `SearchHit.metadata.url` alone).

#### Where metadata is stored

Document-level metadata (`Document.metadata`) is stored in a **dedicated
metadata DB** as one row per document (`DocumentMetadataRepository`). It
is not stored in Lucene's chunk stored fields, so metadata volume does
not grow linearly as the number of chunks increases. See
[architecture.md §5.7](architecture.md) for details.

### 2.4 Search

```java
SearchService searchService = library.getSearchService();

SearchRequest request = SearchRequest.builder()
    .query("形態素解析")
    .namespaceIds(List.of("project-a"))
    .searchType(SearchType.HYBRID)
    .maxResults(10)
    .build();

SearchResult result = searchService.search(request);
result.getHits().forEach(hit ->
    System.out.printf("%s (score=%.2f)%n", hit.getTitle(), hit.getScore()));
```

The asynchronous variant is `searchAsync(request)`.

## 3. Using as a REST API

A reference implementation of the REST API server is in
[`examples/api/`](../examples/api/). For detailed startup steps and
configuration, see [examples/api/README.md](../examples/api/README.md).

### 3.1 Basics

- Base URL: `http://<host>:8080/api/v1`
- Content-Type: `application/json`
- Authentication: setting `searchable.api.key` or `SEARCHABLE_API_KEY`
  makes the `X-API-Key` header required. When unset, the server runs
  without authentication.

### 3.2 Creating a namespace

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "project-a",
    "name": "Project A",
    "config": {
      "architecture": "HYBRID",
      "searchStrategy": "PARALLEL"
    }
  }'
```

List with `GET /api/v1/namespaces`, delete with `DELETE /api/v1/namespaces/{id}`.

### 3.3 Registering documents

Single document:

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "project-a",
    "document": {
      "id": "doc-1",
      "title": "...",
      "content": "..."
    }
  }'
```

Batch:

```bash
curl -X POST http://localhost:8080/api/v1/index/batch \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "project-a",
    "documents": [
      {"id": "doc-1", "title": "...", "content": "..."},
      {"id": "doc-2", "title": "...", "content": "..."}
    ]
  }'
```

### 3.4 Searching

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "形態素解析",
    "namespaceIds": ["project-a"],
    "searchType": "HYBRID",
    "options": {
      "maxResults": 10,
      "highlightEnabled": true
    }
  }'
```

The response includes `hits`, `totalHits`, `maxScore`, and `took`.
For details of each field, see [examples/api/api-specification.md](../examples/api/api-specification.md).

### 3.5 Main endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/search` | Execute a search |
| `GET` / `POST` / `DELETE` | `/api/v1/namespaces[/{id}]` | Namespace CRUD |
| `GET` / `PUT` | `/api/v1/namespaces/{id}/config` | Namespace configuration |
| `POST` / `PUT` / `DELETE` | `/api/v1/index/documents[/{id}]` | Document CRUD |
| `POST` | `/api/v1/index/batch` | Batch registration |
| `POST` | `/api/v1/index/rebuild` | Index rebuild |
| `GET` | `/api/v1/admin/status` | System status |
| `GET` | `/api/v1/admin/metrics` | Metrics |

## 4. Using as an MCP server

A reference implementation of the MCP server is in
[`examples/mcp/`](../examples/mcp/). For the full procedure, see
[examples/mcp/guide.md](../examples/mcp/guide.md).

### 4.1 Startup

stdio mode (for process-launched clients such as Claude Desktop):

```bash
java -jar examples/mcp/target/mcp-example-1.0.1-SNAPSHOT.jar --mode stdio
```

SSE mode (over HTTP):

```bash
java -jar examples/mcp/target/mcp-example-1.0.1-SNAPSHOT.jar --mode sse --port 8080
```

### 4.2 Use from Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "searchable": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-example-1.0.1-SNAPSHOT.jar",
        "--mode",
        "stdio"
      ],
      "env": {
        "SEARCHABLE_CONFIG": "/path/to/config.yaml"
      }
    }
  }
}
```

For details of the provided tools (`search_documents`, etc.), see
[examples/mcp/guide.md](../examples/mcp/guide.md).

## 5. Choosing a search mode

Switch between three modes with `searchType`.
It can be omitted to use the namespace default.

| Mode | Overview | Suitable use cases |
| --- | --- | --- |
| `FULL_TEXT` | Full-text search based on morphological analysis | Keyword matching emphasis / log and code search |
| `VECTOR` | Similarity search via embedding vectors | Find semantically similar content / absorb paraphrasing |
| `HYBRID` | Run both and combine | Balanced approach / general search use cases |

The execution mode for hybrid is specified by the namespace's `searchStrategy`.

- `SEQUENTIAL`: use one side's results to narrow down (specify order with `searchOrder`)
- `PARALLEL`: run concurrently and merge scores

## 6. Highlighting and filtering

Search options control result formatting.

```json
{
  "query": "形態素解析",
  "namespaceIds": ["project-a"],
  "options": {
    "maxResults": 10,
    "offset": 0,
    "highlightEnabled": true
  },
  "filters": {
    "source": "manual.md"
  }
}
```

The left-hand side of `filters` must match the `metadata` key supplied at document registration.

## 7. Managing the index

| Task | API / Operation |
| --- | --- |
| Update | `PUT /api/v1/index/documents/{id}` |
| Delete | `DELETE /api/v1/index/documents/{id}?namespaceId=...` |
| Full rebuild | `POST /api/v1/index/rebuild` |
| Status check | `indexMetadata` of `GET /api/v1/namespaces/{id}` |
| Backup / restore | `POST /api/v1/admin/backup` / `/api/v1/admin/restore` |

For initial loading of large data sets, batch registration (`/index/batch`) is recommended.

## 8. Adding plugins

Placing JARs in `searchable.plugins.directory` loads them at startup.

```properties
searchable.plugins.directory=./plugins
```

The plugin API is defined in the `searchable-plugins` module.
Custom plugins implement the `DataSourcePlugin` interface
([searchable-plugins/src/main/java/io/searchable/plugin/DataSourcePlugin.java](../searchable-plugins/src/main/java/io/searchable/plugin/DataSourcePlugin.java)).

```java
public interface DataSourcePlugin {
    String getName();
    List<Document> fetchDocuments(Map<String, Object> config);
}
```

## 9. Configuration reference

Main configuration keys. For the complete list, see [setup-guide.md](setup-guide.md).

| Key | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | REST API port |
| `searchable.data-directory` | `./data` | Data storage directory |
| `searchable.persistence.type` | `H2` | Metadata DB type |
| `searchable.index.directory` | `./data/indexes` | Lucene index location |
| `searchable.global.default-architecture` | `FULL_TEXT` | Default search method |
| `searchable.global.default-search-strategy` | `SEQUENTIAL` | Execution strategy for hybrid |
| `searchable.plugins.directory` | (unset) | Directory of plugin JARs |

## 10. Error handling

REST API errors use a common format:

```json
{
  "error": {
    "code": "NAMESPACE_NOT_FOUND",
    "message": "Namespace 'project-a' not found",
    "details": {"namespaceId": "project-a"},
    "timestamp": "2026-05-16T10:00:00Z"
  }
}
```

Main error codes:

| Code | HTTP | Suggested response |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | Review the request body |
| `NAMESPACE_NOT_FOUND` | 404 | Create the namespace beforehand |
| `DOCUMENT_NOT_FOUND` | 404 | Verify the ID |
| `NAMESPACE_ALREADY_EXISTS` | 409 | Reuse the existing namespace or choose a different ID |
| `INDEX_ERROR` / `SEARCH_ERROR` / `INTERNAL_ERROR` | 500 | Check server logs and record reproduction conditions |

## 11. Common use cases

- **Internal document search**: operate with 1 namespace per project.
  Base the search UI on the samples under `examples/`.
- **Reference source for AI tools**: invoke from Claude Desktop via the
  MCP server. Use as grounding when generating answers.
- **Multilingual site search**: absorb paraphrasing with `VECTOR` mode
  plus strict keyword matching with `FULL_TEXT`.

## 12. Learn more

- Exhaustive API coverage: [examples/api/api-specification.md](../examples/api/api-specification.md)
- Machine-readable definition: [examples/api/openapi.yaml](../examples/api/openapi.yaml)
- Internal structure: [architecture.md](architecture.md)
- Vector search details: [vector-search-guide.md](vector-search-guide.md)
- Chunking behavior: [chunking-guide.md](chunking-guide.md)
- User dictionary management: [user-dictionary-guide.md](user-dictionary-guide.md)

---

**Document Version**: 1.1
**Last Updated**: 2026-05-16
**Status**: Phases 1–5 complete (module restructuring in progress)
