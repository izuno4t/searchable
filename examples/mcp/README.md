# <img src="../../assets/logo.svg" alt="" width="28" height="28"> Searchable MCP Server (Example)

Sample [Model Context Protocol](https://modelcontextprotocol.io/) server that
exposes `searchable-core`'s hybrid search to AI clients such as Claude
Desktop.

This module is a runnable reference for how to wrap the library as an MCP
server. The CLI speaks JSON-RPC 2.0 over **stdio** and implements the
handshake (`initialize` / `notifications/initialized`), tool discovery
(`tools/list`), tool invocation (`tools/call`), and a `ping` health check.
An optional **SSE** transport (`SseTransport.java`) is provided for
embedding; it is not wired into the CLI of this example.

The Japanese walkthrough lives in [`guide.ja.md`](./guide.ja.md).

## Build

`examples/mcp` is a stand-alone Maven project (not part of the root
reactor). Install the library first, then build:

```bash
mvn -B clean install -DskipTests           # at repository root
mvn -B -f examples/mcp/pom.xml package
```

Artifacts:

- `examples/mcp/target/mcp-example-1.0.0.jar`
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
java -jar examples/mcp/target/mcp-example-1.0.0.jar \
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
`ingest`. The YAML describes **where the index lives**; the source
directory of the documents you want indexed is independent of that and
typically sits somewhere else entirely (your notes folder, a content
repo, an external drive, ...).

```yaml
# searchable.yaml — describes the MCP SERVER'S DATA AREA.
# Every path below points at storage that the MCP server itself owns
# (Lucene index + metadata DB). NONE of these point at the source
# documents to ingest — those are passed as an argument to the
# `ingest` command further down and live wherever your docs actually
# are (~/Documents/handbook, /srv/manuals, ...).

# Root directory for everything the MCP server persists. Must match
# whatever data-directory the writer (cli / api) uses so both
# processes open the same index.
data-directory: ./data/mcp

# Metadata DB (namespace registry, document↔index pointers).
# Stored as an H2 file at ./data/mcp/metadata.mv.db.
persistence:
  type: H2
  url: "jdbc:h2:./data/mcp/metadata;MODE=PostgreSQL"
  username: sa
  password: ""

# Where the Lucene index files are written.
# Lives under data-directory by convention.
index:
  directory: ./data/mcp/indexes

global:
  default-architecture: HYBRID
  default-search-strategy: PARALLEL
  default-search-order: FULL_TEXT_FIRST
```

```bash
# Source path is independent of data-directory; pass any local path
# (typically nothing to do with the server's data area).
./searchable-cli/src/main/scripts/searchable \
  --config ./searchable.yaml \
  ingest --namespace default --source-type file \
  ~/Documents/handbook
```

#### 1b. Using `examples/api`

Boot the [REST API example](../api/) against the same
`data-directory` (set `searchable.data-directory=./data/mcp` in its
`application.properties`) and POST documents as described in
[`examples/api/README.md`](../api/README.md). Shut the API server down
when ingestion is complete; the MCP server will reopen the index
read-only.

### Step 2. Place a `mcp-capabilities.yaml`

Copy the bundled sample next to your `searchable.yaml`, or pass an
absolute path on the next step with `--mcp-capabilities`:

```bash
cp examples/mcp/mcp-capabilities.yaml ./mcp-capabilities.yaml
```

### Step 3. Verify the server starts (no index needed)

Before searching, you can confirm the server boots, completes the
handshake, and advertises its tools. This works **without any indexed
documents** — useful for catching config issues early:

```bash
{
  printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"manual","version":"0.0"}}}'
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
  printf '%s\n' '{"jsonrpc":"2.0","id":3,"method":"ping"}'
} | java -jar examples/mcp/target/mcp-example-1.0.0.jar \
       --config ./searchable.yaml \
       --mcp-capabilities ./mcp-capabilities.yaml
```

Expected (one JSON object per line, formatted here for readability):

```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05",
  "serverInfo":{"name":"searchable-mcp","version":"1.0.0"},
  "capabilities":{"tools":{"listChanged":false}},
  "instructions":"Searchable is a Japanese-optimized hybrid search..."}}
{"jsonrpc":"2.0","id":2,"result":{"tools":[
  {"name":"search_documents","description":"...","inputSchema":{...}},
  {"name":"get_document","description":"...","inputSchema":{...}}]}}
{"jsonrpc":"2.0","id":3,"result":{}}
```

### Step 4. Call `search_documents`

Once the namespace has indexed documents (from Step 1), run the same
pattern with a `tools/call` frame:

```bash
{
  printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"manual","version":"0.0"}}}'
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_documents","arguments":{"query":"形態素解析","namespace_ids":["default"]}}}'
} | java -jar examples/mcp/target/mcp-example-1.0.0.jar \
       --config ./searchable.yaml \
       --mcp-capabilities ./mcp-capabilities.yaml
```

The `tools/call` result's `content[0].text` contains a human-readable
summary like:

```text
検索結果: 2件ヒット (12 ms)

1. [default/doc-001] 形態素解析の基礎
   score: 0.8542
   日本語の形態素解析エンジンは ...

2. [default/doc-007] Sudachi 入門
   score: 0.6213
   辞書ベースの分かち書きを ...
```

In production the client is an AI runtime such as Claude Desktop —
see the next section.

## Claude Desktop integration

Add the server to Claude Desktop's configuration
(`~/Library/Application Support/Claude/claude_desktop_config.json` on
macOS). Use **absolute paths** — Claude Desktop runs the command with its
own working directory, so relative paths and the CWD fallback won't
resolve.

```json
{
  "mcpServers": {
    "searchable": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mcp-example-1.0.0.jar",
        "--config",
        "/absolute/path/to/searchable.yaml",
        "--mcp-capabilities",
        "/absolute/path/to/mcp-capabilities.yaml"
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

Each tool's **name** and **input schema** are defined in Java, in
`src/main/java/io/searchable/example/mcp/tool/`. The **description**
shown to clients can be overridden per tool via the `tools:` section of
`mcp-capabilities.yaml`; absent an override, the Java-side default
description is used. The list of tools the server actually registers is
built in
`src/main/java/io/searchable/example/mcp/SearchableMcpApplication.java`.

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
