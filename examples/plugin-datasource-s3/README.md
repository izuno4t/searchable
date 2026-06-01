# Searchable S3 DataSource Plugin (Example)

Reference implementation of a `DataSourcePlugin` that ingests documents
from an S3-compatible object store. This module is **outside the main
Searchable reactor** — the AWS SDK dependency lives only here.

Use this as a starting point for your own plugin, or copy it into a
separate repository and harden for production.

## Build

```bash
mvn -f examples/plugin-datasource-s3/pom.xml package
```

The produced JAR registers `io.searchable.example.plugin.s3.S3DataSourcePlugin`
under `META-INF/services/io.searchable.plugin.DataSourcePlugin`, so it
will be discovered by `ServiceLoader` once placed on the classpath.

## Configuration keys

Passed through `PluginContext.config()` (per-namespace plugin section
of `application.properties` / YAML).

| Key | Required | Default | Meaning |
| --- | --- | --- | --- |
| `bucket` | yes | — | S3 bucket name |
| `region` | no | `us-east-1` | AWS region |
| `prefix` | no | `` (empty) | Key prefix to scan |
| `endpointOverride` | no | — | URI for S3-compatible stores (MinIO, LocalStack) |

Authentication uses the AWS SDK default credentials provider chain
(env vars, profile, instance profile, ...).

## Wire it up

In an app that embeds `searchable-core`:

```xml
<dependency>
  <groupId>io.searchable.example</groupId>
  <artifactId>plugin-datasource-s3-example</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Then point a namespace at the plugin (key naming follows the host app's
plugin configuration convention):

```properties
searchable.namespaces.default.plugin.name=s3
searchable.namespaces.default.plugin.config.bucket=my-docs
searchable.namespaces.default.plugin.config.region=ap-northeast-1
searchable.namespaces.default.plugin.config.prefix=production/
```

## Tests

```bash
mvn -f examples/plugin-datasource-s3/pom.xml test
```

The included tests mock the S3 client with Mockito — no live AWS or
LocalStack is required.

## End-to-end verification (against MinIO)

To prove the plugin actually loads via `ServiceLoader` and talks to a
real S3 endpoint, follow [verify.md](verify.md). The procedure spins up
a local MinIO container, uploads two sample documents, and runs
`VerifyMain` via `mvn exec:java` — all in roughly a minute.

## Quick start: ingest from S3 into a sample app and search

This plugin has no runtime of its own; it is meant to be loaded by a
host process that embeds `searchable-core`. Below is the end-to-end
flow using [`searchable-cli`](../../searchable-cli/) as the host
(simplest); the same configuration shape works for `examples/api` and
`examples/webapp`.

### Step 1. Build everything

```bash
mvn -B clean install -DskipTests                                # core
mvn -B -f examples/plugin-datasource-s3/pom.xml package         # plugin
mvn -B -f searchable-cli/pom.xml clean package                  # host (CLI fat jar)
```

### Step 2. Put the plugin on the classpath

The CLI itself ships as a single shaded fat jar (see
[docs/adr/0001-cli-executable-jar-with-shade-plugin.md](../../docs/adr/0001-cli-executable-jar-with-shade-plugin.md))
and no longer exposes a `lib/` directory, so plugins must be loaded
through the `plugins.directory` mechanism. Copy the plugin JAR and its
`aws-sdk` transitive dependencies into the directory referenced by the
host's `plugins.directory`:

```bash
mkdir -p ./plugins
cp examples/plugin-datasource-s3/target/*.jar ./plugins/
cp examples/plugin-datasource-s3/target/lib/*.jar ./plugins/
```

### Step 3. Point a namespace at the plugin

```yaml
# searchable.yaml
data-directory: ./data/s3-demo
persistence:
  type: H2
  url: "jdbc:h2:./data/s3-demo/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
index:
  directory: ./data/s3-demo/indexes
plugins:
  directory: ./plugins

namespaces:
  s3docs:
    plugin:
      name: s3
      config:
        bucket: my-docs
        region: ap-northeast-1
        prefix: production/
        # endpointOverride: http://localhost:9000   # for MinIO
```

### Step 4. Ingest and search

```bash
# Ingest via the plugin (source-type=plugin uses the bound DataSourcePlugin)
./searchable-cli/src/main/scripts/searchable \
  --config ./searchable.yaml \
  ingest --namespace s3docs --source-type plugin

# Confirm the index has documents
./searchable-cli/src/main/scripts/searchable \
  --config ./searchable.yaml status
```

To search the freshly built index, point any sample app
(`examples/api`, `examples/webapp`, or `examples/mcp`) at the same
`data-directory` — they will reopen the index transparently.

## Limitations (intentional for an example)

- Object bodies are decoded as UTF-8 text. Binary formats (PDF, Office,
  ...) need additional parsing on the host side.
- No retry / backoff beyond the AWS SDK defaults.
- No support for object versioning or selective re-fetch on change.
- No streaming download for very large objects (uses `getObjectAsBytes`).

For production use, fork this example and address those points.
