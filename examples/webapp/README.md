# Searchable Webapp (Example)

Library-embedded webapp built on Spring Boot + Thymeleaf. The same JVM
process owns the Lucene index and serves the search UI — the
"all-in-one" deployment pattern from
[docs/devel/design/architecture/overview.md §7.2](../../docs/devel/design/architecture/overview.md).

## Run

`examples/webapp` is a stand-alone Maven project (not part of the root
reactor). Install the library first, then build and launch:

```bash
mvn -B clean install -DskipTests           # at repository root
mvn -B -f examples/webapp/pom.xml package
java -jar examples/webapp/target/webapp-example-1.0.0-SNAPSHOT.jar
```

The app listens on `http://localhost:8080`.

## Quick start: index and search

The webapp does not expose a write REST API — it is meant to be the
"all-in-one" deployment, so indexing happens either through the
startup batch ingest or by ingesting via `searchable-cli` into the
same `data-directory`.

Note the distinction throughout this section:

- **Source directory** — wherever your documents actually live
  (`~/Documents/handbook`, `/var/data/manuals`, an external drive, ...).
  Usually nothing to do with the webapp itself.
- **Index directory** — under the webapp's `data-directory`
  (default `./data/webapp`). Owned by the webapp; you do not put
  source documents here.

The two are independent and almost always live in different places.

### Option A. Boot-time batch ingest

Point the webapp at the directory where your documents live and turn
on the ingest flag. The webapp opens this directory **read-only** and
writes the resulting index under `./data/webapp/indexes`.

```bash
# Source directory: wherever the documents actually live.
SOURCE_DIR=~/Documents/handbook

java -jar examples/webapp/target/webapp-example-1.0.0-SNAPSHOT.jar \
     --searchable.ingest.enabled=true \
     --searchable.ingest.namespace=default \
     --searchable.ingest.source="$SOURCE_DIR"
```

Markdown, plain text, HTML, and PDF are recognised. The webapp scans
the directory once on startup and indexes everything into the `default`
namespace. Subsequent boots can omit `--searchable.ingest.enabled=true`
because the index is persisted under `./data/webapp`.

### Option B. Index with `searchable-cli`

Use [`searchable-cli`](../../searchable-cli/) when you want to ingest
without restarting the webapp, or to ingest large data sets.
The CLI's `searchable.yaml` only describes **where the index lives**;
the source path is passed as the last argument of `ingest` and can be
anywhere on disk.

```yaml
# searchable.yaml — describes the WEBAPP'S DATA AREA.
# Every path below points at storage that the webapp itself owns
# (Lucene index + metadata DB). NONE of these point at the source
# documents to ingest — those are passed as an argument to the
# `ingest` command further down and live wherever your docs actually
# are (~/Documents/handbook, /srv/manuals, ...).

# Root directory for everything the webapp persists. Must match the
# webapp's `searchable.data-directory` so both processes open the same
# index.
data-directory: ./data/webapp

# Metadata DB (namespace registry, document↔index pointers).
# Stored as an H2 file at ./data/webapp/metadata.mv.db.
persistence:
  type: H2
  url: "jdbc:h2:./data/webapp/metadata;MODE=PostgreSQL"
  username: sa
  password: ""

# Where the Lucene index files are written.
# Lives under data-directory by convention.
index:
  directory: ./data/webapp/indexes
```

```bash
# The source directory below is independent of the data directory above.
# It points at wherever the documents to ingest actually live.
./searchable-cli/src/main/scripts/searchable \
  --config ./searchable.yaml \
  ingest --namespace default --source-type file \
  ~/Documents/handbook
```

> Stop the webapp before running searchable-cli writes if you are using the
> default H2 metadata store (H2 in embedded mode is single-writer).
> Switch the persistence URL to TCP or use PostgreSQL when both
> processes need to be online simultaneously.

### Search

Open <http://localhost:8080/> and type a query into the search box, or
hit the URL directly:

```bash
curl 'http://localhost:8080/?q=%E5%BD%A2%E6%85%8B%E7%B4%A0%E8%A7%A3%E6%9E%90'
```

Per-document detail is available at
`GET /documents/{namespace}/{id}`.

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
