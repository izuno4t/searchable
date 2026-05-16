# Searchable

A Japanese-optimized hybrid search library for Java that combines full-text
search and vector search in a single embeddable package.

Searchable is built for teams who need first-class Japanese language support
without standing up a heavyweight search cluster. The library is the primary
deliverable; runnable REST API and MCP (Model Context Protocol) server
references live in [`examples/`](examples/), so it fits both application
back-ends and AI-tool integrations.

## Why Searchable

- **Japanese first**: Kuromoji / Sudachi morphological analysis and
  Japanese-optimized embedding models (multilingual-e5) are wired in by
  default — no extra configuration to handle particles, auxiliary verbs,
  or mixed-byte text.
- **Hybrid search built in**: full-text and vector search can be used
  independently, sequentially, or in parallel with score fusion. Strategy
  is configurable per index.
- **Embeddable, not infrastructure**: in-memory Lucene-based architecture
  targets <500ms responses for 100k documents. Ship as a JAR, or run one
  of the reference apps under [`examples/`](examples/) (Spring Boot
  webapp, REST API server, MCP server).
- **Local-first AI**: vector embeddings are generated in-process with ONNX
  Runtime. No external API keys, no data leaving the host.
- **Multi-tenant by design**: Namespaces give each tenant or dataset its
  own logical index with isolated configuration.
- **Pluggable data sources**: implement the
  [`DataSourcePlugin`](searchable-plugins/src/main/java/io/searchable/plugin/DataSourcePlugin.java)
  interface in `searchable-plugins` to ingest from custom sources.

## Features

| Capability | Detail |
| --- | --- |
| Full-text search | Apache Lucene + Kuromoji / Sudachi, BM25 scoring with per-namespace overrides |
| Vector search | Lucene HNSW + ONNX Runtime + multilingual-e5 |
| Hybrid search | Sequential or parallel execution, configurable per namespace |
| Document formats | Plain Text / Markdown / AsciiDoc / PDF / HTML |
| Interfaces | Java API (core); CLI (`searchable-cli`); REST API / MCP / webapp as reference apps in [`examples/`](examples/) |
| Persistence | H2 (default) or PostgreSQL metadata via HikariCP + file-system Lucene indexes |
| Operations | Backup / restore of Lucene indexes, user dictionary management, admin UI (`searchable-admin`) |

## Technology Stack

Java 21, Maven, Apache Lucene, ONNX Runtime, Spring Boot, SLF4J + Logback,
H2 / PostgreSQL (HikariCP). Admin UI uses Thymeleaf; the sample search UI
under `examples/search-ui/` is plain HTML + JS.

## Modules

| Module | Role |
| --- | --- |
| `searchable-core` | Core library: indexing, search, namespaces, persistence |
| `searchable-plugins` | Plugin API (`DataSourcePlugin` and friends) |
| `searchable-ai` | Embedding / ONNX integration for vector search |
| `searchable-cli` | Command-line interface for index management |
| `searchable-admin` | Admin UI (Spring Boot + Thymeleaf) |
| `searchable-testkit` | Shared test fixtures for downstream apps |
| `examples/api` | Reference REST API server (Spring Boot) |
| `examples/mcp` | Reference MCP server (stdio + SSE) |
| `examples/webapp` | Reference embedded webapp |
| `examples/search-ui` | Static HTML/JS client for the REST API |

## Project Status

- **Version**: 1.0.0-SNAPSHOT
- **Implementation phases (1–5)** are complete; their task lists are
  archived under [`docs/archives/`](docs/archives/).
- Module layout is currently being restructured; some user-facing docs
  may briefly lag behind the source tree during the refactor.

Roadmap and phase planning live in
[`docs/project-plan.md`](docs/project-plan.md).

## Documentation

Pick the document that matches what you want to do:

| Document | When to read it |
| --- | --- |
| [Getting Started](docs/getting-started.ja.md) | First-time setup — build, run, and execute a search in a few minutes |
| [Usage Guide](docs/usage.ja.md) | Day-to-day reference for the Java API, REST API, and MCP server |
| [Setup Guide](docs/setup-guide.md) | Detailed installation, configuration, and operational tasks |
| [CLI Guide](docs/cli-guide.ja.md) | `searchable-cli` reference for index management from the shell |
| [Admin UI Guide](docs/admin-ui-guide.md) | Operating the `searchable-admin` Spring Boot UI |
| [Architecture](docs/architecture.md) | Design rationale and internal structure |
| [Examples Overview](examples/README.md) | Reference apps: webapp / REST API / MCP / search UI |
| [API Specification](examples/api/api-specification.ja.md) | Full REST / Java / MCP API specification |
| [OpenAPI](examples/api/openapi.yaml) | Machine-readable REST API definition |
| [Requirements](docs/requirements.md) | Functional and non-functional requirements |
| [Research Reports](docs/research/) | Background investigations behind key technical decisions |

## License

To be determined.
