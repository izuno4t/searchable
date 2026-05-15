# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Japanese-optimized hybrid search library for Java. Provides full-text search (Lucene + Kuromoji/Sudachi) and vector search (ONNX Runtime + multilingual-e5).

**Status**: Phase 1 planning - documentation only, no source code yet

## Build Commands

```bash
mvn clean package             # Build
mvn test                      # Run tests
mvn test -Dtest=Class#method  # Single test
```

## Architecture

```text
searchable-core/     # Core library (domain/application/infrastructure layers)
searchable-api/      # REST API (Spring Boot)
searchable-mcp/      # MCP Server
searchable-plugins/  # Plugin API
```

## Key Technologies

Java 21, Maven, Apache Lucene, ONNX Runtime, Spring Boot, SLF4J+Logback

## Documentation

See `docs/` for details:

- `requirements.md` - Requirements specification
- `architecture.md` - Architecture design
- `task-phase1.md` - Phase 1 task list

## Working Rules

- **Do not run `git commit`, `git push`, or any git command that mutates
  history.** The user owns commits in this repository and these commands
  are also blocked by the harness permission settings.
- Stage changes if it helps preview a commit (`git add`, `git status`,
  `git diff`), but stop there and let the user run the commit.
