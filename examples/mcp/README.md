# Searchable MCP Server (Example)

Sample [Model Context Protocol](https://modelcontextprotocol.io/) server that
exposes `searchable-core`'s hybrid search to AI clients such as Claude
Desktop.

This module is a runnable reference for how to wrap the library as an MCP
server. It implements the JSON-RPC 2.0 handshake (`initialize` /
`notifications/initialized`), tool discovery (`tools/list`), tool invocation
(`tools/call`), and a `ping` health check over both the stdio and the SSE
transports.

The Japanese walkthrough lives in [`guide.ja.md`](./guide.ja.md).

## Build

`examples/mcp` is a stand-alone Maven project (not part of the root
reactor). Install the library first, then build:

```bash
mvn -B clean install -DskipTests           # at repository root
mvn -B -f examples/mcp/pom.xml package
```

Artifacts:

- `examples/mcp/target/mcp-example-1.0.0-SNAPSHOT.jar`
- `examples/mcp/target/lib/` (runtime dependencies)

## Configuration

The server reads two YAML files. They are intentionally split so that the
search engine settings (which `searchable-core` owns) stay separate from the
MCP-specific identity information.

| File | Owner | Purpose |
| --- | --- | --- |
| `searchable.yaml` | `searchable-core` | Data directory, persistence, index, plugin, and global search defaults. Same schema as the REST API example. |
| `mcp-capabilities.yaml` | this module | MCP `InitializeResult` payload — the `serverInfo` (`Implementation`) and `capabilities` (`ServerCapabilities`) blocks returned to the client during the handshake. |

### `searchable.yaml`

Minimal example:

```yaml
data-directory: ./data
persistence:
  type: H2
  url: "jdbc:h2:./data/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
index:
  directory: ./data/indexes
plugins:
  directory: ./plugins
global:
  default-architecture: HYBRID
  default-search-strategy: PARALLEL
  default-search-order: FULL_TEXT_FIRST
```

### `mcp-capabilities.yaml`

This file is a **pure override layer**. The Java code provides sensible
defaults; every field below is optional unless noted.

```yaml
server-info:
  name: searchable-mcp        # required
  # version: 1.0.0            # optional — defaults to the Maven version

instructions: |               # optional — surfaced in InitializeResult
  Use Searchable when the user's question may be answered by their own
  indexed documents.

capabilities:                 # passed through verbatim to the client
  tools:
    listChanged: false

tools:                        # optional per-tool description overrides
  search_documents:
    description: |
      Search the user's document namespaces. Default to HYBRID search.
```

Field-by-field behaviour:

| Field | Required | Default |
| --- | --- | --- |
| `server-info.name` | yes | — |
| `server-info.version` | no | Maven version from `searchable-mcp.properties` (filtered at build time) |
| `instructions` | no | Omitted from `InitializeResult` |
| `capabilities` | no | Empty object |
| `tools.<name>.description` | no | The Java-side default from each `McpTool.definition()` |

The `protocolVersion` field of `InitializeResult` is **not** configurable
here — it is bound to the server implementation and updates together
with the code when this example moves to a newer MCP specification.

### Resolution order

The capabilities file is resolved in this order at startup:

1. `--mcp-capabilities <path>` CLI argument
2. `./mcp-capabilities.yaml` in the current working directory

The server refuses to start if neither is available. The sample file at
[`mcp-capabilities.yaml`](./mcp-capabilities.yaml) in this module is
intentionally **not** bundled inside the JAR; copy it next to your
`searchable.yaml` (or point at it with `--mcp-capabilities`) when
deploying.

## Run

The MCP client launches the server as a child process and speaks stdio;
manual invocation is useful for debugging:

```bash
java -jar examples/mcp/target/mcp-example-1.0.0-SNAPSHOT.jar \
  --config /absolute/path/to/searchable.yaml \
  --mcp-capabilities /absolute/path/to/mcp-capabilities.yaml
```

- JSON-RPC requests are read from `stdin`, one per line.
- JSON-RPC responses are written to `stdout`, one per line.
- All logs go to `stderr` so they do not contaminate the protocol stream.

## Quick start: populate the index and search

> **The MCP server is read-only.** It exposes `search_documents` and
> `get_document` but has no write tools — there is no MCP method that
> indexes documents. You must build the index out of band (with
> `searchable-cli` or with the [`examples/api`](../api/) server) before
> the MCP tools return anything.

### Step 1. Build an index

Pick **one** of the two approaches below. Both write to the same Lucene
index on disk; the MCP server only needs to point at the same
`data-directory`.

#### 1a. Using `searchable-cli` (simplest)

Create a `searchable.yaml` that the MCP server will also use, then run
`ingest`:

```yaml
# searchable.yaml
data-directory: ./data/mcp
persistence:
  type: H2
  url: "jdbc:h2:./data/mcp/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
index:
  directory: ./data/mcp/indexes
global:
  default-architecture: HYBRID
  default-search-strategy: PARALLEL
  default-search-order: FULL_TEXT_FIRST
```

```bash
./searchable-cli/src/main/scripts/searchable \
  --config ./searchable.yaml \
  ingest --namespace default --source-type file ./path/to/docs
```

#### 1b. Using `examples/api`

Boot the [REST API example](../api/) against the same
`data-directory` (set `searchable.data-directory=./data/mcp` in its
`application.properties`) and POST documents as described in
[`examples/api/README.md`](../api/README.md). Shut the API server down
when ingestion is complete; the MCP server will reopen the index
read-only.

### Step 2. Start the MCP server

Use the `--config` produced in Step 1:

```bash
java -jar examples/mcp/target/mcp-example-1.0.0-SNAPSHOT.jar \
  --config ./searchable.yaml \
  --mcp-capabilities ./mcp-capabilities.yaml
```

### Step 3. Call `search_documents`

For a manual smoke test, pipe JSON-RPC frames into stdin. The example
below performs the handshake, lists tools, then calls
`search_documents`:

```bash
{
  printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"manual","version":"0.0"}}}'
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_documents","arguments":{"query":"形態素解析","namespace_ids":["default"]}}}'
} | java -jar examples/mcp/target/mcp-example-1.0.0-SNAPSHOT.jar \
       --config ./searchable.yaml \
       --mcp-capabilities ./mcp-capabilities.yaml
```

In production the client is an AI runtime such as Claude Desktop —
see the next section.

## Claude Desktop integration

Add the server to Claude Desktop's configuration
(`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "searchable": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mcp-example-1.0.0-SNAPSHOT.jar",
        "--config",
        "/absolute/path/to/searchable.yaml"
      ]
    }
  }
}
```

Restart Claude Desktop. The tools `search_documents` and `get_document`
appear in the tool palette, and the server identifies itself with the
`server-info.name` value from `mcp-capabilities.yaml`.

## Tools

### `search_documents`

Searches the documents indexed in Searchable namespaces.

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `query` | string | yes | Search query text |
| `namespace_ids` | string[] | no | Target namespaces (empty / omitted = all) |
| `search_type` | string | no | `FULL_TEXT` / `VECTOR` / `HYBRID` (defaults to the namespace setting) |
| `max_results` | integer | no | Maximum number of hits (default `10`) |

### `get_document`

Fetches a single document by its namespace and id, returning title,
indexed-at timestamp, and a snippet of the body.

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `namespace_id` | string | yes | Namespace that contains the document |
| `document_id` | string | yes | Identifier of the document to fetch |

The Java side of each tool definition (input schema, description, name)
lives in `src/main/java/io/searchable/example/mcp/tool/`. The list of
tools that the server actually registers is built in `SearchableMcpApplication`
(see `src/main/java/io/searchable/example/mcp/SearchableMcpApplication.java`).

## Implemented JSON-RPC methods

- `initialize` — handshake; returns the payload defined by
  `mcp-capabilities.yaml` plus the bound `protocolVersion`.
- `notifications/initialized` — client-side initialization acknowledged.
- `tools/list` — advertises the registered tools.
- `tools/call` — invokes a registered tool.
- `ping` — liveness check.

## Tests

```bash
cd examples/mcp
mvn test
```

The suite covers the JSON-RPC handshake and dispatch loop, the SSE
transport, capability YAML loading, and an end-to-end search integration
test that goes through `SearchService` against an in-memory Lucene index.
