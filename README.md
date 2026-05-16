# Searchable

A Japanese-optimized hybrid search library for Java that combines full-text
search and vector search in a single embeddable package.

Searchable is built for teams who need first-class Japanese language support
without standing up a heavyweight search cluster. It ships as a library, a
REST API server, and an MCP (Model Context Protocol) server, so it fits both
application back-ends and AI-tool integrations.

## Why Searchable

- **Japanese first**: Kuromoji / Sudachi morphological analysis and
  Japanese-optimized embedding models (multilingual-e5) are wired in by
  default — no extra configuration to handle particles, auxiliary verbs,
  or mixed-byte text.
- **Hybrid search built in**: full-text and vector search can be used
  independently, sequentially, or in parallel with score fusion. Strategy
  is configurable per index.
- **Embeddable, not infrastructure**: in-memory Lucene-based architecture
  targets <500ms responses for 100k documents. Ship as a JAR, run as a
  Spring Boot server, or expose to AI tools through MCP.
- **Local-first AI**: vector embeddings are generated in-process with ONNX
  Runtime. No external API keys, no data leaving the host.
- **Multi-tenant by design**: Namespaces give each tenant or dataset its
  own logical index with isolated configuration.
- **Pluggable data sources**: add custom document sources via a small
  plugin interface (see `examples/filesystem-plugin/`).

## Features

| Capability | Detail |
| --- | --- |
| Full-text search | Apache Lucene + Kuromoji / Sudachi |
| Vector search | Lucene HNSW + ONNX Runtime + multilingual-e5 |
| Hybrid search | Sequential or parallel execution, configurable per namespace |
| Document formats | Plain Text / Markdown / AsciiDoc (Phase 1) — PDF / HTML coming in Phase 2 |
| Interfaces | Java API, REST API, MCP Server |
| Persistence | H2-backed metadata + file-system Lucene indexes |

## Technology Stack

Java 21, Maven, Apache Lucene, ONNX Runtime, Spring Boot, SLF4J + Logback.
Admin UI uses Thymeleaf; the sample search UI is built with React.

## Project Status

- **Current Phase**: Phase 1 (Full-text search core)
- **Phase 1 Status**: Complete
- **Version**: 1.0.0-SNAPSHOT

Roadmap and phase planning live in
[`docs/project-plan.md`](docs/project-plan.md).

## Documentation

Pick the document that matches what you want to do:

| Document | When to read it |
| --- | --- |
| [Getting Started](docs/getting-started.ja.md) | First-time setup — build, run, and execute a search in a few minutes |
| [Usage Guide](docs/usage.ja.md) | Day-to-day reference for the Java API, REST API, and MCP server |
| [Setup Guide](docs/setup-guide.md) | Detailed installation, configuration, and operational tasks |
| [Architecture](docs/architecture.md) | Design rationale and internal structure |
| [API Specification](docs/api-specification.md) | Full REST / Java / MCP API specification |
| [Requirements](docs/requirements.md) | Functional and non-functional requirements |
| [OpenAPI](docs/openapi.yaml) | Machine-readable REST API definition |
| [Research Reports](docs/research/) | Background investigations behind key technical decisions |

## License

To be determined.
