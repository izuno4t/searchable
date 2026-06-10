# Multi-tenancy Guide

Searchable supports multi-tenancy through **Namespaces**: each tenant
or dataset gets its own logical index with isolated Analyzer,
embedding, and persistence configuration. This guide documents what
Namespaces actually isolate — and, more importantly, what they do
**not** — so that you can decide whether they fit your tenancy model
before exposing them to untrusted tenants.

## Isolation model

| Boundary | Isolated? | Notes |
| --- | --- | --- |
| Lucene index files | Yes | Each Namespace owns a separate directory under the configured index root |
| Analyzer / embedding strategy | Yes | Per-Namespace `searchable.yaml` configuration |
| Metadata rows (HikariCP / H2 / PostgreSQL) | Yes (logical) | Rows are tagged with `namespace_id`; the schema is shared |
| JVM heap / thread pool | **No** | All Namespaces share one process, one heap, one executor |
| File descriptors / `MMapDirectory` mappings | **No** | Open searchers across Namespaces compete for the same FD budget |
| CPU / disk I/O | **No** | No QoS or rate limiting between Namespaces |
| At-rest encryption | **No** | Index files and metadata are stored in plaintext |
| Authentication / authorization | **No** | The library does not authenticate callers; the embedding application owns access control |
| Outbound traffic from `AiProvider` | **No** | If an external provider (Anthropic / OpenAI) is enabled, retrieved hits leave the host — see §5 below |

In short: Namespaces are a **logical** boundary inside one JVM, not a
process-, container-, or cluster-level isolation.

## Constraints you must plan for

### 1. OOM risk is shared across tenants

Lucene `IndexWriter`, segment merges, and HNSW vector indexes allocate
on the shared JVM heap. A single Namespace with a runaway segment
merge can `OutOfMemoryError` the whole process and take every other
Namespace down with it.

- Cap per-Namespace document count or index size at the application
  layer if you accept tenant-supplied data.
- Configure `-Xmx` with headroom for the **sum** of expected
  Namespaces, not the largest one.
- Trigger explicit `forceMerge` only when no other Namespace is
  serving high-traffic queries.

### 2. Noisy-neighbor effect (no QoS)

Searchable does **not** apply rate limiting, query-cost accounting, or
priority queues across Namespaces. One Namespace running expensive
phrase queries or kNN searches with large `k` will increase latency
for every other Namespace on the same process.

- If you need fairness, run one process per high-value tenant and
  point each at its own `searchable.yaml`.
- For shared deployments, enforce per-tenant request budgets and
  query timeouts in the embedding application.

### 3. No at-rest encryption

Lucene segment files, vector files, and the metadata DB are written
in plaintext.

- If your threat model requires confidentiality at rest, deploy on an
  encrypted filesystem (LUKS, dm-crypt, APFS encrypted volume) or an
  encrypted block device (EBS gp3 with KMS, GCE PD with CMEK, ...).
- Per-tenant encryption (different keys per Namespace) is not
  available; this is tracked in `BACKLOG-002`.

### 4. No built-in authentication / authorization

The Java API trusts every caller. Namespaces only isolate **data**,
not **access**. It is the embedding application's responsibility to:

- Authenticate the caller (OIDC / API key / mTLS / ...)
- Authorize which Namespaces each caller may read or write
- Enforce per-Namespace query and ingest budgets

The reference REST API and MCP server under
[`examples/`](../../examples/) demonstrate one possible wiring, but
they are illustrative and not hardened for hostile tenants.

### 5. External AI providers send tenant data off-host

`searchable-ai` ships three `AiProvider` implementations for post-search
summarization / Q&A:

| Provider | Endpoint | Data flow |
| --- | --- | --- |
| `AnthropicProvider` | `https://api.anthropic.com` | Query + retrieved hits sent to Anthropic |
| `OpenAiProvider` | `https://api.openai.com/v1` | Query + retrieved hits sent to OpenAI |
| `OllamaProvider` | `http://localhost:11434` (configurable) | Stays on the host (or wherever the Ollama daemon runs) |

No provider is selected by default. **Enabling Anthropic or OpenAI
means that document content from the calling Namespace leaves the host
in plaintext over HTTPS**, subject to the provider's data-retention
and training-use policies. For multi-tenant deployments this has
consequences beyond Searchable's own isolation model:

- **Data residency / regulatory scope** (GDPR, 個人情報保護法,
  HIPAA, financial sector rules) — sending tenant documents to a
  US-based LLM provider may breach contractual or statutory
  residency requirements. Confirm the chosen provider's region,
  zero-retention option, and DPA before enabling it for a regulated
  tenant.
- **Per-tenant opt-in is the calling application's job** — there is
  no per-Namespace flag in the library to allow / deny `AiProvider`
  usage. The embedding application must decide, per request, whether
  the caller's data may be shared with the configured provider.
- **Prefer Ollama (or another self-hosted provider) for sensitive
  tenants** — Ollama keeps the prompt on the host. Implementing a
  custom `AiProvider` against a self-hosted inference endpoint is the
  standard path when external providers are not acceptable.

## When Namespaces are enough

- Single-tenant deployment with multiple **datasets** (e.g. "support",
  "engineering", "marketing") in the same process.
- Trusted multi-tenant deployment where every tenant is internal and
  performance isolation is not a contractual requirement.
- Development / staging environments where you want one index process
  per developer or feature branch.

## When Namespaces are not enough

- Untrusted multi-tenant SaaS where one tenant must not be able to
  starve another for CPU, memory, or latency.
- Workloads requiring per-tenant encryption keys (HIPAA / PCI DSS /
  regional data residency).
- Strict SLA per tenant with QoS guarantees.

For these scenarios, run one Searchable process per tenant (or per
tenant cohort) and put the routing layer above Searchable.

## Related documents

- [`docs/public/setup-guide.md`](setup-guide.md) — environment-level
  hardening (filesystem encryption, JVM sizing)
