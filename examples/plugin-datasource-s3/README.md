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

## Limitations (intentional for an example)

- Object bodies are decoded as UTF-8 text. Binary formats (PDF, Office,
  ...) need additional parsing on the host side.
- No retry / backoff beyond the AWS SDK defaults.
- No support for object versioning or selective re-fetch on change.
- No streaming download for very large objects (uses `getObjectAsBytes`).

For production use, fork this example and address those points.
