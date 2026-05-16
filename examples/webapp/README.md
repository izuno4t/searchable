# Searchable Webapp (Example)

Library-embedded webapp built on Spring Boot + Thymeleaf. The same JVM
process owns the Lucene index and serves the search UI — the
"all-in-one" deployment pattern from
[docs/architecture.md §7.2](../../docs/architecture.md).

## Run

```bash
mvn -pl examples/webapp -am package
java -jar examples/webapp/target/webapp-example-1.0.0-SNAPSHOT-boot.jar
```

The app listens on `http://localhost:8080`.

## Configuration

`application.properties` exposes the most useful knobs:

| Property | Default | Meaning |
| --- | --- | --- |
| `searchable.data-directory` | `./data/webapp` | Root directory for index + DB |
| `searchable.persistence.url` | `jdbc:h2:./data/webapp/metadata;MODE=PostgreSQL` | Metadata DB JDBC URL |
| `searchable.ingest.enabled` | `false` | Run the startup batch ingest |
| `searchable.ingest.namespace` | `default` | Namespace to populate |
| `searchable.ingest.source` | `./data/sample` | Directory to crawl |

Set environment variables (`SEARCHABLE_DATA_DIRECTORY=...`) to override
without editing the file.

## Pages

- `GET /` — search box and result list (Thymeleaf)
- `GET /?q=<query>` — execute a search
- `GET /documents/{namespace}/{id}` — single document detail

## Tests

```bash
cd examples/webapp
mvn test
```

The suite boots Spring with an in-memory H2 backend and validates that
the public pages render without errors.
