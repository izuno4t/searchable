# Searchable CLI

Command-line tool (`searchable`) for managing Searchable indexes from a
shell. It is a thin wrapper around
[`searchable-core`](../searchable-core/) and exposes ingestion,
deletion, rebuild, status inspection, backup / restore, plugin
discovery, and config validation as subcommands.

For the long-form Japanese reference (options, exit codes, examples for
every subcommand), see [`docs/public/cli-guide.ja.md`](../docs/public/cli-guide.ja.md).

## Requirements

- Java 21 or newer
- A `searchable.yaml` application config (see
  [`docs/public/setup-guide.md`](../docs/public/setup-guide.md))

## Build

```bash
mvn -pl searchable-cli -am clean package
```

Build outputs:

- `searchable-cli/target/searchable-cli-1.0.1-SNAPSHOT.jar` — main jar
- `searchable-cli/target/lib/` — runtime dependencies (copied by
  `maven-dependency-plugin`)
- `searchable-cli/src/main/scripts/searchable` — launcher shell script

## Run

The launcher resolves the runtime classpath in this order:

1. `$SEARCHABLE_HOME/lib/*` when `SEARCHABLE_HOME` is set
2. `./lib/*` co-located with the launcher (the layout produced by
   `mvn package`)
3. `target/classes` + `target/lib/*` when invoked from a developer
   checkout

```bash
# From a developer checkout
./searchable-cli/src/main/scripts/searchable \
  --config ./config/searchable.yaml --help

# From a packaged distribution
SEARCHABLE_HOME=/opt/searchable \
  /opt/searchable/bin/searchable --config /etc/searchable.yaml status
```

You can also invoke the jar directly:

```bash
java -jar searchable-cli/target/searchable-cli-1.0.1-SNAPSHOT.jar \
  --config ./config/searchable.yaml --help
```

## Common Options

| Option | Description |
| --- | --- |
| `-c`, `--config <path>` | Path to the YAML application config (required for every subcommand) |
| `-h`, `--help` | Show help and exit |
| `-V`, `--version` | Show version and exit |

## Subcommands

| Subcommand | Purpose |
| --- | --- |
| `ingest` | Index a single file, an entire directory, or a data-source plugin batch |
| `delete` | Delete a document from the index by id |
| `rebuild` | Clear all documents from a namespace so it can be re-ingested |
| `status` | Print per-namespace document counts and disk usage (read-only) |
| `backup` | Snapshot Lucene indexes to a destination directory |
| `restore` | Restore Lucene indexes from a backup directory |
| `list-plugins` | List discovered Searchable plugins (read-only) |
| `validate-config` | Parse and validate the YAML config (dry run, no resources opened) |

### Examples

```bash
# Ingest a directory tree into the "docs" namespace
searchable --config searchable.yaml ingest \
  --namespace docs \
  --source-type file \
  --id-prefix manual- \
  ./path/to/source

# Delete a single document
searchable --config searchable.yaml delete \
  --namespace docs --id manual-foo.md

# Wipe a namespace before re-ingesting
searchable --config searchable.yaml rebuild --namespace docs

# Inspect aggregate index statistics
searchable --config searchable.yaml status

# Snapshot and restore Lucene indexes
searchable --config searchable.yaml backup  --target /var/backups/searchable
searchable --config searchable.yaml restore --source /var/backups/searchable

# Discover loadable plugins
searchable --config searchable.yaml list-plugins

# Dry-run the config file
searchable --config searchable.yaml validate-config
```

Run `searchable <subcommand> --help` for the full option list of any
subcommand.

## Logging

The CLI bundles its own `logback.xml`; the default log level is `INFO`.
Override with the `SEARCHABLE_LOG_LEVEL` environment variable, for
example `SEARCHABLE_LOG_LEVEL=DEBUG`.

## Exit Codes

| Code | Meaning |
| --- | --- |
| `0` | Success |
| `1` | Subcommand-specific logical error (e.g. `delete` target missing) |
| `2` | Configuration or argument error |
| other | picocli-defined codes (see `--help`) |

## Related Documentation

- [`docs/public/cli-guide.ja.md`](../docs/public/cli-guide.ja.md) — Detailed reference
  (Japanese)
- [`docs/public/setup-guide.md`](../docs/public/setup-guide.md) — Config file format
- [`docs/public/usage.ja.md`](../docs/public/usage.ja.md) — Using Searchable as a Java
  library
- [`docs/devel/design/architecture/overview.md`](../docs/devel/design/architecture/overview.md)
  — Storage layer and module layout
