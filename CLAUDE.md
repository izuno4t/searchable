# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Japanese-optimized hybrid search library for Java. Provides full-text
search (Apache Lucene + Kuromoji) and vector search (ONNX Runtime +
multilingual-e5) from a single embeddable JAR.

**Status**: Pre-1.0 (`1.0.0-SNAPSHOT`). Phases 1–5 implementation is
complete; M3 (AI integration) and the 2026-06 review response are in
progress. See [`docs/devel/work/tasks/`](docs/devel/work/tasks/).

## Build Commands

```bash
./mvnw -B clean install              # Build all modules
./mvnw -B test                       # Run tests
./mvnw -B test -Dtest=Class#method   # Single test
./mvnw -B -Pquality verify           # Checkstyle + SpotBugs
./mvnw -B -Psecurity verify          # OWASP dependency-check (CVSS≥7 fails)
./mvnw -B -f examples/api/pom.xml package   # Build a reference example
```

## Architecture

Maven multi-module under root `searchable-parent` (`io.searchable`):

```text
searchable-plugins/   # Plugin SPI (DataSourcePlugin, AiProvider)
searchable-core/      # Core: indexing, search, namespaces, persistence
searchable-ai/        # Embedding / ONNX integration for vector search
searchable-testkit/   # Shared test fixtures
searchable-cli/       # Command-line interface (picocli)
searchable-admin/     # Admin UI (Spring Boot + Thymeleaf)
```

Reference applications under `examples/` (not registered in the root
`<modules>`; build individually):

```text
examples/api/                  # REST API server (Spring Boot)
examples/mcp/                  # MCP server (stdio JSON-RPC)
examples/webapp/               # Embedded webapp (Spring Boot)
examples/search-ui/            # Static HTML/JS client for the REST API
examples/plugin-datasource-s3/ # Sample DataSourcePlugin (S3)
```

## Key Technologies

- Java 21, Maven (multi-module), `./mvnw` wrapper
- Apache Lucene 10.4 (`lucene-core`, `analysis-kuromoji`, `queryparser`, `highlighter`)
- ONNX Runtime 1.26 (`com.microsoft.onnxruntime:onnxruntime`)
- Spring Boot 3.5 (api / admin / webapp examples)
- Persistence: H2 (default) or PostgreSQL, both via HikariCP
- Document parsers: PDFBox, jsoup, Apache POI
- Logging: SLF4J 2.x + Logback 1.5 (Log4j 2 → SLF4J bridge for POI)
- JSON / YAML: Jackson, SnakeYAML
- Tests: JUnit 5, AssertJ, Mockito, Testcontainers
- Quality: Checkstyle, SpotBugs, JaCoCo, OWASP dependency-check, cspell, markdownlint

## Documentation

Doc layout follows the repository docs guideline (v1.0). Start from
[`docs/devel/README.md`](docs/devel/README.md).

- [`docs/devel/requirements.md`](docs/devel/requirements.md) — Requirements
- [`docs/devel/specs/`](docs/devel/specs/) — Public I/F contracts (Java API, SPI, CLI, config, metadata, search behavior)
- [`docs/devel/design/`](docs/devel/design/) — Current design (architecture / application)
- [`docs/devel/adr/`](docs/devel/adr/) — Architecture Decision Records
- [`docs/devel/testing/`](docs/devel/testing/) — Test policy and verification steps
- [`docs/devel/operation/`](docs/devel/operation/) — Operational procedures
- [`docs/devel/work/tasks/`](docs/devel/work/tasks/) — In-progress task lists
- [`docs/devel/work/plans/`](docs/devel/work/plans/) — Multi-task plans (roadmap etc.)
- [`docs/devel/work/investigations/`](docs/devel/work/investigations/) — Research reports
- [`docs/devel/work/archive/`](docs/devel/work/archive/) — Completed task records
- [`docs/public/`](docs/public/) — User-facing guides (getting-started, usage, CLI, etc.)

## Working Rules

- **Do not run `git commit`, `git push`, or any git command that mutates
  history.** The user owns commits in this repository and these commands
  are also blocked by the harness permission settings.
- Stage changes if it helps preview a commit (`git add`, `git status`,
  `git diff`), but stop there and let the user run the commit.

## Document Language Conventions

Documents are classified by audience:

- **For users** (利用者向け: README, getting-started, usage, API specs,
  guides, sample app READMEs, etc.) — provided in both English and
  Japanese.
  - `xxx.md` is the **English** version (default)
  - `xxx.ja.md` is the **Japanese** version
  - Write English first; the Japanese version is created when explicitly
    requested as a translation.
- **For developers / contributors** (作り手向け: requirements specs,
  architecture design, project plans, task lists, ADRs, internal notes,
  scratch design memos) — written in **Japanese only**.
  - File extension is plain `.md` (no `.ja.md` suffix needed because
    there is no English counterpart).
