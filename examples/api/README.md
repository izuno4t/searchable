# Searchable REST API (Example)

Sample REST API server (Spring Boot) on top of `searchable-core`.

This module is a runnable reference for how to build a production-style API
that wraps the library. The provided endpoints cover hybrid search, index
management, namespace administration, and user dictionary upkeep, with
optional API key authentication and CORS.

## Run

```bash
mvn -pl examples/api -am package
java -jar examples/api/target/api-example-1.0.0-SNAPSHOT.jar \
     --spring.config.location=examples/api/application.properties
```

The server listens on `http://localhost:8080` by default.

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
