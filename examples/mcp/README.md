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

```bash
mvn -pl examples/mcp -am package
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

```yaml
server-info:
  name: searchable-mcp
  version: 1.0.0

capabilities:
  tools: {}
```

The `protocolVersion` field of `InitializeResult` is bound to the server
implementation and is not configurable here — it is updated together with
the code when this example upgrades to a newer MCP specification.

`capabilities` is passed through verbatim. Future MCP options such as
`tools.listChanged` or `resources.listChanged` can be enabled by editing
this file alone:

```yaml
capabilities:
  tools:
    listChanged: true
  resources:
    listChanged: false
```

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
