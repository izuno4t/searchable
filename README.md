# Searchable - Hybrid Search Library for Java

## Overview

Searchable is a lightweight, high-performance search library for Java
that provides both full-text search and vector search capabilities,
**optimized for Japanese language**. It is designed to be easily
embedded into web applications, used as a standalone REST API server,
or integrated with AI tools via MCP (Model Context Protocol).

## Key Features

- **Japanese Language Optimized**: Japanese morphological analysis
  (Kuromoji/Sudachi) and Japanese-optimized embedding models
- **Hybrid Search**: Combine full-text search and vector search,
  or use them independently
- **Multi-Tenant Support**: Logical index isolation using Namespaces
- **Multiple API Options**: Java API, REST API, and MCP Server
- **High Performance**: In-memory architecture for fast response times
  (target: <500ms for 100k documents)
- **Plugin Architecture**: Extensible data sources and custom search
  processing
- **Embedded AI**: Built-in vector embeddings using Onnx Runtime
  (no external API required)

## Use Cases

- Add search functionality to web applications without deploying heavy
  search engines
- Create document search servers for AI tools (Claude, ChatGPT, etc.)
- Unified search across multiple document sources
- Local-first semantic search with privacy

## Architecture

### Search Capabilities

#### Full-Text Search

- Powered by Apache Lucene
- **Japanese morphological analysis** using Kuromoji or Sudachi
- Proper tokenization for Japanese text (particles, auxiliary verbs)
- Multi-byte character support (UTF-8)
- Field-specific search, fuzzy search, wildcards
- Highlighting for Japanese text

#### Vector Search

- Embedded vector generation using Onnx Runtime
- **Japanese-optimized multilingual embedding models**
  (multilingual-e5-small, etc.)
- Semantic similarity search
- No external API dependencies

#### Hybrid Search

- Sequential: Full-text → Vector or Vector → Full-text
- Parallel: Execute both searches simultaneously and merge results
- Configurable search strategies per Namespace

### Namespace System

- Logical index isolation
- Per-Namespace configuration (search type, strategy, etc.)
- Global default settings with Namespace overrides
- Dynamic creation and deletion via API

### Supported Document Formats

#### Phase 1 (MVP)

- Plain Text
- Markdown
- AsciiDoc

#### Phase 2

- PDF
- HTML

#### Future

- Microsoft Office (Word, Excel, PowerPoint)
- Google Docs, Apple Pages

## APIs

### Java API

```java
SearchableLibrary library = SearchableLibrary.builder()
    .configPath("/path/to/config.yaml")
    .build();

SearchService searchService = library.getSearchService();

SearchRequest request = SearchRequest.builder()
    .query("search query")
    .namespaceIds(List.of("namespace1"))
    .searchType(SearchType.HYBRID)
    .maxResults(10)
    .build();

SearchResult result = searchService.search(request);
```

### REST API

```bash
# Search
POST /api/search
{
  "query": "search query",
  "namespaceIds": ["namespace1"],
  "searchType": "HYBRID",
  "options": {
    "maxResults": 10
  }
}

# Create Namespace
POST /api/namespaces
{
  "id": "namespace1",
  "name": "Project A",
  "config": {
    "architecture": "HYBRID",
    "searchStrategy": "PARALLEL"
  }
}
```

### MCP Server

```json
{
  "mcpServers": {
    "searchable": {
      "command": "java",
      "args": ["-jar", "/path/to/searchable-mcp.jar", "--mode", "stdio"]
    }
  }
}
```

## Technology Stack

- **Language**: Java 21
- **Build Tool**: Maven
- **Full-Text Search**: Apache Lucene + Kuromoji/Sudachi
- **Vector Search**: Lucene HNSW + Onnx Runtime + multilingual-e5
- **Database**: H2 (Phase 1)
- **Logging**: SLF4J + Logback
- **REST API**: Spring Boot
- **Admin UI**: Thymeleaf (Phase 3)
- **Search UI**: React (sample implementation, Phase 3)

## Performance Targets

- **Search Response**: <500ms (single Namespace, 100k documents)
- **Document Capacity**: Up to 100,000 documents per Namespace
- **Concurrent Connections**: Environment-dependent
- **Index Size**: Up to several GB in-memory mode

## Development Phases

### Phase 1: Full-Text Search Core (0.5 months)

- Full-text search engine (Lucene)
- Namespace management
- Java API & REST API
- Plugin architecture
- Data persistence (File system / DB)

### Phase 2: Vector Search (0.5 months)

- Vector search engine (Lucene HNSW)
- Onnx Runtime integration
- Hybrid search (Sequential & Parallel)
- MCP server
- PDF & HTML support

### Phase 3: Admin UI (0.5 months)

- Namespace management UI
- Index management UI
- System configuration UI
- Monitoring dashboard

## Plugin Architecture

Extend Searchable with custom data sources:

```java
public interface DataSourcePlugin {
    String getName();
    List<Document> fetchDocuments(Map<String, Object> config);
}
```

Deploy plugins by placing JAR files in the plugins directory.
See `examples/filesystem-plugin/` for a reference implementation.

## Deployment Options

### Embedded Library Mode

```xml
<dependency>
  <groupId>com.searchable</groupId>
  <artifactId>searchable-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Standalone Server Mode

```bash
java -jar searchable-server.jar --config=/path/to/config.yaml
```

### MCP Server Mode

```bash
java -jar searchable-mcp.jar --mode stdio
```

## Quick Start

### Prerequisites

- Java 21 or later
- Maven 3.9+

### Build

```bash
mvn -B clean package
```

This produces three JARs:

- `searchable-plugins/target/searchable-plugins-1.0.0-SNAPSHOT.jar`
- `searchable-core/target/searchable-core-1.0.0-SNAPSHOT.jar`
- `searchable-api/target/searchable-api-1.0.0-SNAPSHOT.jar`
  (Spring Boot fat jar)

### Run the REST API server

```bash
java -jar searchable-api/target/searchable-api-1.0.0-SNAPSHOT.jar
```

The server listens on `http://localhost:8080`.

### Try the API

```bash
# Create a namespace
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{"id":"sample","name":"Sample","config":{"architecture":"FULL_TEXT"}}'

# Index a document
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId":"sample",
    "document":{
      "id":"doc-1",
      "title":"Searchable",
      "content":"日本語形態素解析に対応した全文検索ライブラリです。"
    }
  }'

# Search
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"形態素解析","namespaceIds":["sample"]}'
```

## Test

```bash
mvn -B test
```

## License

To be determined.

## Project Status

- **Current Phase**: Phase 1 (Full-text search core)
- **Phase 1 Status**: Complete
- **Version**: 1.0.0-SNAPSHOT

## Documentation

- [Requirements Specification](docs/requirements.md)
- [Architecture Design](docs/architecture.md)
- [API Specification](docs/api-specification.md)
- [Project Plan](docs/project-plan.md)
- [Phase 1 Task List](docs/task-phase1.md)
- [Setup Guide](docs/setup-guide.md)
- [Research Reports](docs/research/)
- [OpenAPI](docs/openapi.yaml)
