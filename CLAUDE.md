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

- `REQUIREMENTS.md` - Requirements specification
- `ARCHITECTURE.md` - Architecture design
- `TASK-PHASE1.md` - Phase 1 task list
