# Searchable REST API (Example)

Sample REST API server (Spring Boot) on top of `searchable-core`.

This module is a runnable reference for how to build a production-style API
that wraps the library. The provided endpoints cover hybrid search, index
management, namespace administration, and user dictionary upkeep, with
optional API key authentication and CORS.

## Run

`examples/api` is a stand-alone Maven project (not part of the root
reactor), so use `-f` to point at its POM. The build requires
`searchable-core` to be installed in the local `~/.m2` first:

```bash
mvn -B clean install -DskipTests           # at repository root
mvn -B -f examples/api/pom.xml package
java -jar examples/api/target/api-example-1.0.0-SNAPSHOT.jar \
     --spring.config.location=examples/api/application.properties
```

The server listens on `http://localhost:8080` by default.

## Quick start: index and search

End-to-end walkthrough: create a namespace, index a document, and
search it — all via `curl`.

### Step 1. Create a namespace

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "quickstart",
    "name": "Quickstart",
    "config": {"architecture": "FULL_TEXT"}
  }'
```

### Step 2. Index a document

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "quickstart",
    "document": {
      "id": "doc-1",
      "title": "Searchable について",
      "content": "Searchable は日本語形態素解析に対応した全文検索ライブラリです。"
    }
  }'
```

For larger imports use `POST /api/v1/index/batch` (see
[`api-specification.ja.md`](./api-specification.ja.md)).

### Step 3. Search

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "形態素解析",
    "namespaceIds": ["quickstart"]
  }'
```

The response contains a `hits` array; `doc-1` should appear.

### Alternative: index with `searchable-cli`

For bulk ingestion (or to pre-build an index before the server boots),
use [`searchable-cli`](../../searchable-cli/) against the **same
`data-directory`** as the API server.

The API server's defaults come from
[`application.properties`](./src/main/resources/application.properties);
create a matching `searchable.yaml` for the CLI:

```yaml
data-directory: ./data/api
persistence:
  type: H2
  url: "jdbc:h2:./data/api/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
index:
  directory: ./data/api/indexes
```

Then ingest a directory tree (or a single file) and search via the API
as in Step 3:

```bash
./searchable-cli/src/main/scripts/searchable \
  --config ./searchable.yaml \
  ingest --namespace quickstart --source-type file ./path/to/docs
```

> The CLI also supports `delete`, `rebuild`, `status`, `backup`, and
> `restore`. See [`searchable-cli/README.md`](../../searchable-cli/README.md).

## Authentication

When the configuration property `searchable.api.key` (or the environment
variable `SEARCHABLE_API_KEY`) is set, every request must include the
`X-API-Key` HTTP header with a matching value. Leave the key unset to
disable authentication for local development.

## CORS

`searchable.cors.allowed-origins` enables Cross-Origin Resource Sharing
for the configured origins. Leave the list empty to keep the API
single-origin.

## API surface

- `POST /api/v1/search` — hybrid search
- `POST /api/v1/index/documents` — index a single document
- `POST /api/v1/index/batch` — bulk index
- `DELETE /api/v1/index/documents/{id}` — delete a document
- `POST /api/v1/index/rebuild` — clear a namespace
- `GET /api/v1/index/{namespaceId}/metadata` — index metadata
- `GET /api/v1/namespaces` / `POST /api/v1/namespaces` — namespace CRUD
- `GET /api/v1/dictionaries` — dictionary management

See [`api-specification.ja.md`](./api-specification.ja.md) for the full
contract, and [`openapi.yaml`](./openapi.yaml) for the machine-readable
schema (importable into Swagger UI or Postman).

## Tests

```bash
cd examples/api
mvn test
```

The suite includes integration tests for the controller surface, hybrid
search behavior, and a basic search-performance regression check.
