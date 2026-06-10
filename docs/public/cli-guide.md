# Searchable CLI guide

This guide describes how to use the command-line tool (`searchable`)
provided by the `searchable-cli` module. It covers ingestion,
deletion, rebuilding, status inspection, backup/restore, plugin
inspection, and configuration validation for indexes.

## 1. Prerequisites

- Java 21 or later
- A configuration file in `searchable.yaml` format (see, for example, `docs/public/setup-guide.md`)

## 2. Build and distribution

```bash
mvn -B -f searchable-cli/pom.xml clean package
# Artifacts:
#   searchable-cli/target/searchable-cli-1.0.1-SNAPSHOT.jar          (executable fat jar)
#   searchable-cli/target/original-searchable-cli-1.0.1-SNAPSHOT.jar (pre-shade original, for reference)
#   searchable-cli/src/main/scripts/searchable                       (launcher shell script)
```

The CLI is distributed as a single executable fat jar built with
`maven-shade-plugin`. The jar alone can be launched with `java -jar`;
there is no need to place a sibling `lib/` directory.

Direct launch:

```bash
java -jar searchable-cli/target/searchable-cli-1.0.1-SNAPSHOT.jar \
  --config /path/to/searchable.yaml <subcommand> [args]
```

When using the bundled launcher shell script
`searchable-cli/src/main/scripts/searchable`, the script resolves the
fat jar in the following order, using the first one found:

1. `$SEARCHABLE_HOME/searchable-cli.jar`
2. `searchable-cli.jar` next to the shell script
3. In a development checkout, `searchable-cli/target/searchable-cli-*.jar`
   (the `original-*.jar` left by shade is excluded)

## 3. Common options

| Option | Description |
| --- | --- |
| `-c`, `--config <path>` | Path to the YAML configuration file (required for every subcommand) |
| `-h`, `--help` | Show help |
| `-V`, `--version` | Show version |

## 4. Subcommands

### ingest

Recursively walks a file or directory and ingests it through
`searchable-core`.

```bash
searchable --config searchable.yaml ingest \
  --namespace docs \
  --source-type file \
  --id-prefix manual- \
  path/to/source
```

Options:

- `--namespace`: target Namespace ID
- `--source-type`: plugin name (`file` is the built-in filesystem plugin)
- `--id-prefix`: prefix for generated document IDs (optional)

Each file is automatically tagged with reserved metadata keys:

| Key | Value | Source |
| --- | --- | --- |
| `url` | `file:///<absolute-path>` (RFC 3986 URI) | `Path.toUri()` |
| `path` | Absolute file path (string) | `Path.toAbsolutePath()` |
| `contentType` | MIME (e.g., `text/markdown`, `application/pdf`) | Defined by the parser |

`metadata.url` is used for direct links from search results, and
`metadata.contentType` is used by the UI to switch the display and as
the base for generating section anchors.

### delete

```bash
searchable --config searchable.yaml delete --namespace docs --id manual-foo.md
```

### rebuild

Deletes every document under a Namespace. Intended for clearing
before re-ingestion.

```bash
searchable --config searchable.yaml rebuild --namespace docs
```

> The internal implementation does not stop search: it keeps serving
> searches from the old index version, writes a new empty version
> directory, and switches over by atomically renaming the directory on
> completion. The old version is deleted after a grace period of 30
> seconds by default.

### status

Opens every Namespace in read-only mode and reports the document
count and disk usage.

```bash
searchable --config searchable.yaml status
```

### backup / restore

Invokes `BackupService` / `RestoreService`.

```bash
searchable --config searchable.yaml backup --target /var/backups/searchable
searchable --config searchable.yaml restore --source /var/backups/searchable
```

### list-plugins

Prints the list of plugins currently discoverable on the classpath
via `PluginLoader.overview()`.

```bash
searchable --config searchable.yaml list-plugins
```

### validate-config

Loads the configuration file and prints the parsed result (dry run).

```bash
searchable --config searchable.yaml validate-config
```

On a configuration error it exits with code 2 and prints a
`Configuration error: ...` message.

## 5. Log output

The CLI bundles a `logback.xml` and defaults to the `INFO` level. Set
the environment variable `SEARCHABLE_LOG_LEVEL=DEBUG` to switch to
verbose output.

## 6. Exit codes

| Code | Meaning |
| --- | --- |
| 0 | Successful completion |
| 1 | Subcommand-specific logical error (for example, no target for `delete`) |
| 2 | Configuration or argument error |
| Other | Follows picocli conventions (check with `--help`) |

## 7. Related documents

- [docs/public/setup-guide.md](setup-guide.md) — Configuration file syntax
- [docs/public/usage.md](usage.md) — Use as a library
