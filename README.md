# Searchable - Hybrid Search Library for Java

## Overview

Searchable is a lightweight, high-performance search library for Java that provides both full-text search and vector search capabilities, **optimized for Japanese language**. It's designed to be easily embedded into web applications, used as a standalone REST API server, or integrated with AI tools via MCP (Model Context Protocol).

## Key Features

- **Japanese Language Optimized**: Japanese morphological analysis (Kuromoji/Sudachi) and Japanese-optimized embedding models
- **Hybrid Search**: Combine full-text search and vector search, or use them independently
- **Multi-Tenant Support**: Logical index isolation using Namespaces
- **Multiple API Options**: Java API, REST API, and MCP Server
- **High Performance**: In-memory architecture for fast response times (target: <500ms for 100k documents)
- **Plugin Architecture**: Extensible data sources and custom search processing
- **Embedded AI**: Built-in vector embeddings using Onnx Runtime (no external API required)

## Use Cases

- Add search functionality to web applications without deploying heavy search engines
- Create document search servers for AI tools (Claude, ChatGPT, etc.)
- Unified search across multiple document sources
- Local-first semantic search with privacy

## Architecture

### Search Capabilities

**Full-Text Search**
- Powered by Apache Lucene
- **Japanese morphological analysis** using Kuromoji or Sudachi
- Proper tokenization for Japanese text (particles, auxiliary verbs)
- Multi-byte character support (UTF-8)
- Field-specific search, fuzzy search, wildcards
- Highlighting for Japanese text

**Vector Search**
- Embedded vector generation using Onnx Runtime
- **Japanese-optimized multilingual embedding models** (multilingual-e5-small, etc.)
- Semantic similarity search
- No external API dependencies

**Hybrid Search**
- Sequential: Full-text → Vector or Vector → Full-text
- Parallel: Execute both searches simultaneously and merge results
- Configurable search strategies per Namespace

### Namespace System

- Logical index isolation
- Per-Namespace configuration (search type, strategy, etc.)
- Global default settings with Namespace overrides
- Dynamic creation and deletion via API

### Supported Document Formats

**Phase 1 (MVP)**
- Plain Text
- Markdown
- AsciiDoc

**Phase 2**
- PDF
- HTML

**Future**
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
- **Full-Text Search**: Apache Lucene + Kuromoji/Sudachi (Japanese morphological analyzer)
- **Vector Search**: Lucene HNSW + Onnx Runtime + multilingual-e5 (Japanese-optimized)
- **Database**: H2 / SQLite / RocksDB (to be selected)
- **Logging**: SLF4J + Logback
- **REST API**: Spring Boot
- **Admin UI**: Thymeleaf
- **Search UI**: React (sample implementation)

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

## License

To be determined

## Project Status

- **Current Phase**: Phase 1 Preparation
- **Version**: 1.0.0-SNAPSHOT
- **Status**: Under Development

## Documentation

- [Requirements Specification](REQUIREMENTS.md)
- [Architecture Design](ARCHITECTURE.md)
- [API Specification](API_SPECIFICATION.md)
- [Project Plan](PROJECT_PLAN.md)

## Contact

Project maintained by NRI Netcom Corporation
