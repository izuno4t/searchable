# 🔍 Searchable

![Java](https://img.shields.io/badge/Java-21-orange)
![Build](https://img.shields.io/badge/Build-Maven-c71a36)
![Lucene](https://img.shields.io/badge/Lucene-10.2-blue)
![MCP Protocol](https://img.shields.io/badge/MCP-2024--11--05-blueviolet)
![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-brightgreen)

> 🇯🇵 **Japanese-optimized hybrid search for Java** — full-text and vector
> search in a single embeddable JAR.

Searchable is built for teams who need first-class Japanese language support
without standing up a heavyweight search cluster. The library is the primary
deliverable; runnable REST API and MCP (Model Context Protocol) server
references live in [`examples/`](examples/), so it fits both application
back-ends and AI-tool integrations.

---

## ✨ Why Searchable

- 🇯🇵 **Japanese first** — Kuromoji / Sudachi morphological analysis and
  Japanese-optimized embedding models (multilingual-e5) are wired in by
  default. No extra configuration to handle particles, auxiliary verbs,
  or mixed-byte text.
- 🎯 **Hybrid search built in** — full-text and vector search can be used
  independently, sequentially, or in parallel with score fusion. Strategy
  is configurable per index.
- 📦 **Embeddable, not infrastructure** — in-memory Lucene-based
  architecture targets <500ms responses for 100k documents. Ship as a
  JAR, or run one of the reference apps under [`examples/`](examples/)
  (Spring Boot webapp, REST API server, MCP server).
- 🔒 **Local-first AI** — vector embeddings are generated in-process with
  ONNX Runtime. No external API keys, no data leaving the host.
- 🏢 **Multi-tenant by design** — Namespaces give each tenant or dataset
  its own logical index with isolated configuration.
- 🧩 **Pluggable data sources** — implement the
  [`DataSourcePlugin`](searchable-plugins/src/main/java/io/searchable/plugin/DataSourcePlugin.java)
  interface in `searchable-plugins` to ingest from custom sources.

---

## 🚀 Quick Start

Get a working search endpoint running in a few minutes via the bundled
REST API example.

### Prerequisites

| Requirement | Version |
| --- | --- |
| Java | 21+ |
| Maven | 3.9+ (or use the bundled `./mvnw`) |
| Memory | 1 GB free |
| OS | macOS / Linux / Windows |

### 1. Clone & build

```bash
git clone https://github.com/izuno4t/searchable.git
cd searchable
./mvnw -B clean install -DskipTests
./mvnw -B -f examples/api/pom.xml package
```

The first command installs the core library into your local `~/.m2`;
the second packages the REST API example as a fat JAR.

### 2. Start the REST API server

```bash
java -jar examples/api/target/api-example-1.0.0-SNAPSHOT.jar
```

When you see `Started SearchableApplication`, the server is listening on
port `8080`. Sanity check:

```bash
curl http://localhost:8080/api/v1/namespaces
# => {"namespaces":[],"total":0}
```

### 3. Create a namespace, index a doc, search it

```bash
# Create a namespace
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{"id":"quickstart","name":"Quickstart","config":{"architecture":"FULL_TEXT"}}'

# Index a document
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{"namespaceId":"quickstart","document":{
        "id":"doc-1",
        "title":"About Searchable",
        "content":"Searchable は日本語形態素解析に対応した全文検索ライブラリです。"}}'

# Search
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"形態素解析","namespaceIds":["quickstart"]}'
```

You should see `doc-1` returned in the `hits` array. 🎉

For the MCP server flow (Claude Desktop integration) see
[`examples/mcp/README.md`](examples/mcp/README.md).

---

## 🔧 Use as a Library

Drop Searchable into a Maven project as a regular dependency:

```xml
<dependency>
  <groupId>io.searchable</groupId>
  <artifactId>searchable-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Minimal embedded usage (loads `searchable.yaml`, opens the index, runs
a search, and closes everything via try-with-resources):

```java
ApplicationConfig config = new ConfigLoader().load(Path.of("searchable.yaml"));

try (SearchableLibrary library = SearchableLibrary.fromConfig(config)) {
    SearchResult result = library.searchService().search(
        SearchRequest.builder()
            .query("形態素解析")
            .namespaceIds(List.of("quickstart"))
            .build());

    System.out.println(result.totalHits() + " hits in " + result.tookMs() + " ms");
    result.hits().forEach(h ->
        System.out.println("  " + h.documentId() + " — " + h.title()));
}
```

The `SearchableLibrary.Builder` exposes individual overrides (custom
embedding provider, in-memory repositories for tests, etc.); see
[`SearchableLibrary.java`](searchable-core/src/main/java/io/searchable/core/SearchableLibrary.java)
for the full surface.

---

## ✅ Features

| Capability | Detail |
| --- | --- |
| Full-text search | Apache Lucene + Kuromoji / Sudachi, BM25 scoring with per-namespace overrides |
| Vector search | Lucene HNSW + ONNX Runtime + multilingual-e5 |
| Hybrid search | Sequential or parallel execution, configurable per namespace |
| Document formats | Plain Text / Markdown / AsciiDoc / PDF / HTML |
| Interfaces | Java API (core); CLI (`searchable-cli`); REST API / MCP / webapp as reference apps in [`examples/`](examples/) |
| Persistence | H2 (default) or PostgreSQL metadata via HikariCP + file-system Lucene indexes |
| Operations | Backup / restore of Lucene indexes, user dictionary management, admin UI (`searchable-admin`) |

---

## ⚡ Performance

Measured on synthetic Japanese corpora; see
[`docs/research/task-003-performance.md`](docs/research/task-003-performance.md)
and [`docs/research/task-123-vector-performance.md`](docs/research/task-123-vector-performance.md)
for the full setups.

| Workload | Scale | p99 | Max | Over 500 ms |
| --- | --- | --- | --- | --- |
| Full-text search | 100k docs | 1 ms | 1 ms | 0 / 1,000 |
| Vector search | 100k docs | 0 ms | 1 ms | 0 / 1,000 |
| REST API search | 5k docs | 7 ms (p95) | 17 ms | 0 / N |
| Initial index build | 100k docs | — | 6 s (full-text) / 88 s (vector) | — |

> 🎯 Target was 500 ms / 100k docs — actual results are **3 orders of
> magnitude** below target. Bench environment: Java 21, Apple Silicon,
> `MMapDirectory`, Lucene 10.2.

---

## 🛠️ Technology Stack

Java 21 · Maven · Apache Lucene 10.2 · ONNX Runtime · Spring Boot · SLF4J + Logback ·
H2 / PostgreSQL (HikariCP). Admin UI uses Thymeleaf; the sample search UI
under `examples/search-ui/` is plain HTML + JS.

---

## 🧩 Modules

| Module | Role |
| --- | --- |
| `searchable-core` | Core library: indexing, search, namespaces, persistence |
| `searchable-plugins` | Plugin API (`DataSourcePlugin` and friends) |
| `searchable-ai` | Embedding / ONNX integration for vector search |
| `searchable-cli` | Command-line interface for index management |
| `searchable-admin` | Admin UI (Spring Boot + Thymeleaf) |
| `searchable-testkit` | Shared test fixtures for downstream apps |
| `examples/api` | Reference REST API server (Spring Boot) |
| `examples/mcp` | Reference MCP server (stdio JSON-RPC) |
| `examples/webapp` | Reference embedded webapp |
| `examples/search-ui` | Static HTML / JS client for the REST API |

---

## 📍 Project Status

**Pre-1.0 (`1.0.0-SNAPSHOT`).** Core API surface is stable enough to
build against; minor breaking changes are still possible before the 1.0
tag. Module layout is being tidied as the codebase approaches release.

The Phase 1–5 implementation plans (documented under
[`docs/archives/`](docs/archives/)) are complete; roadmap and follow-up
work live in [`docs/project-plan.md`](docs/project-plan.md).

---

## 📚 Documentation

> Many internal references are still Japanese-only (`.ja.md`); a full
> English translation pass is on the roadmap. English entry points are
> listed first below.

| Document | Language | When to read it |
| --- | --- | --- |
| [Setup Guide](docs/setup-guide.md) | en | Detailed installation, configuration, and operational tasks |
| [Architecture](docs/architecture.md) | en | Design rationale and internal structure |
| [Admin UI Guide](docs/admin-ui-guide.md) | en | Operating the `searchable-admin` Spring Boot UI |
| [Vector Search Guide](docs/vector-search-guide.md) | en | Embeddings, HNSW, and hybrid scoring |
| [Examples Overview](examples/README.md) | ja | Reference apps: webapp / REST API / MCP / search UI |
| [Getting Started](docs/getting-started.ja.md) | ja | First-time setup in 5–10 minutes |
| [Usage Guide](docs/usage.ja.md) | ja | Day-to-day reference for the Java API, REST API, and MCP server |
| [CLI Guide](docs/cli-guide.ja.md) | ja | `searchable-cli` reference for index management |
| [API Specification](examples/api/api-specification.ja.md) | ja | Full REST / Java / MCP API specification |
| [OpenAPI](examples/api/openapi.yaml) | — | Machine-readable REST API definition |
| [Requirements](docs/requirements.md) | en | Functional and non-functional requirements |
| [Research Reports](docs/research/) | mixed | Background investigations behind key technical decisions |

---

## 💬 Support

- 🐛 **Bugs & feature requests** —
  [GitHub Issues](https://github.com/izuno4t/searchable/issues)
- 💡 **Questions & design discussion** —
  [GitHub Discussions](https://github.com/izuno4t/searchable/discussions)
  (enable from the repository if not yet active)
- 📨 **Direct contact** — open an issue with `[contact]` in the title

Before filing a bug, please include: Java version, OS, the failing
command / code, and the relevant log lines from `stderr`.

---

## 📜 License

⚠️ **License is not yet finalized.** Until a `LICENSE` file is added,
treat this codebase as *all rights reserved* — please contact the
maintainer before redistributing, vendoring, or deploying it in a
production setting.

The intent is to ship an OSS-friendly license at 1.0; track progress in
[GitHub Issues](https://github.com/izuno4t/searchable/issues).
