# Searchable + Ollama (Example)

Configuration reference for running `searchable-admin` against a local
[Ollama](https://ollama.com) server for AI summarisation.

This directory ships only sample configuration files and notes — no
Maven build. The companion code is
`io.searchable.ai.ollama.OllamaProvider` in `searchable-ai`, registered
via `ServiceLoader` and selected through `searchable.ai.provider=ollama`.

## What this example shows

- Pointing `searchable-admin`'s `AiProvider` SPI at a local Ollama
  server (default endpoint `http://localhost:11434/api/generate`).
- Switching models and timeouts without code changes.
- Optional: running Ollama itself in a container via the bundled
  `docker-compose.yml`.
- Optional: routing the same workload through `OpenAiProvider` against
  Ollama's OpenAI-compatible endpoint at `/v1/chat/completions`.

## Prerequisites

- [Ollama](https://ollama.com/download) installed locally, OR Docker
  Engine to run the bundled compose file.
- Searchable installed into your local Maven repo:

  ```bash
  mvn -B clean install -DskipTests
  ```

## Quick start

### Step 1. Start Ollama and pull a model

Either run the installer's background service, or use the bundled
container:

```bash
docker compose -f examples/ai-ollama/docker-compose.yml up -d
docker exec -it searchable-ollama ollama pull llama3.2
```

Confirm the model is loaded:

```bash
curl -sS http://localhost:11434/api/tags | jq '.models[].name'
```

### Step 2. Apply the Ollama property block to searchable-admin

The file [`application.properties`](application.properties) in this
directory contains only the `searchable.ai.*` block needed to switch
the admin from "AI disabled" to "AI via Ollama". Either:

- merge those lines into
  `searchable-admin/src/main/resources/application.properties`, or
- drop the file in as a Spring profile:

  ```bash
  cp examples/ai-ollama/application.properties \
     searchable-admin/src/main/resources/application-ai-ollama.properties
  ```

  …and start the admin with the profile activated:

  ```bash
  mvn -B -pl searchable-admin spring-boot:run \
      -Dspring-boot.run.arguments=--spring.profiles.active=ai-ollama
  ```

### Step 3. Verify in the admin UI

Open <http://localhost:8080/settings/ai>. The `Provider` dropdown
should list `ollama`; the form should reflect `enabled=true` and the
model you configured. Once a namespace contains indexed documents, the
admin's summarise action routes through Ollama.

Smoke-test the Ollama endpoint directly to confirm reachability:

```bash
curl -sS http://localhost:11434/api/generate \
  -H 'Content-Type: application/json' \
  -d '{"model":"llama3.2","prompt":"Say hi","stream":false}' \
  | jq -r '.response'
```

## Configuration reference

Bound to Spring (`SearchableProperties.Ai`):

| Property | Default | Notes |
| --- | --- | --- |
| `searchable.ai.enabled` | `false` | Master switch. Must be `true`. |
| `searchable.ai.provider` | (empty) | Set to `ollama`. |
| `searchable.ai.model` | (empty) | Empty → falls through to `OLLAMA_DEFAULT_MODEL` → `llama3.2`. |
| `searchable.ai.timeout` | `15s` | Wall-clock per call; raise for slower local hardware. |
| `searchable.ai.max-tokens` | `512` | Maps to Ollama `options.num_predict`. |
| `searchable.ai.temperature` | `0.2` | Maps to Ollama `options.temperature`. |
| `searchable.ai.max-context-items` | `5` | Cap on indexed hits passed to the LLM. |
| `searchable.ai.max-context-chars` | `8000` | Hard char limit after the item cap. |
| `searchable.ai.fallback-on-error` | `true` | Empty summary on `TIMEOUT` / `UPSTREAM`. |

Read directly by `OllamaProvider` (not bound to Spring):

| Source | Key | Default |
| --- | --- | --- |
| Env var | `OLLAMA_BASE_URL` | `http://localhost:11434` |
| Env var | `OLLAMA_DEFAULT_MODEL` | `llama3.2` |
| JVM `-D` | `searchable.ai.ollama.base-url` | (same as env) |
| JVM `-D` | `searchable.ai.ollama.default-model` | (same as env) |

Example: point the admin at a remote Ollama and use a different default
model in one shot.

```bash
OLLAMA_BASE_URL=http://ollama.internal:11434 \
OLLAMA_DEFAULT_MODEL=qwen2.5 \
mvn -B -pl searchable-admin spring-boot:run
```

## Failure modes and fallback

`OllamaProvider` produces `AiException` of these kinds, mapped from the
HTTP layer in `HttpProviderSupport`:

| Kind | Trigger | `fallback-on-error=true` behaviour |
| --- | --- | --- |
| `TIMEOUT` | `HttpTimeoutException` | Empty summary, `model="ai-fallback"`. |
| `UPSTREAM` | 5xx, malformed JSON | Empty summary, `model="ai-fallback"`. |
| `REQUEST` | 400 / 404 / 422 / 429 | Always rethrown (likely misconfig). |
| `UNKNOWN` | Other I/O failure | Empty summary, `model="ai-fallback"`. |

Ollama is unauthenticated by default, so `AUTH` is not produced here.
If you front it with a reverse proxy that requires credentials, the
proxy's 401 / 403 will surface as `AUTH` and always rethrow.

## Alternative: OpenAI-compatible route

Ollama also exposes an OpenAI-compatible API at `/v1/chat/completions`
(see <https://ollama.com/blog/openai-compatibility>). You can therefore
point `OpenAiProvider` at it instead of the native `OllamaProvider`:

```bash
OPENAI_BASE_URL=http://localhost:11434/v1 \
OPENAI_API_KEY=ollama \
mvn -B -pl searchable-admin spring-boot:run
```

…with `searchable.ai.provider=openai` and `searchable.ai.model=llama3.2`
in your properties. The dummy `OPENAI_API_KEY=ollama` value is required
because `OpenAiProvider` rejects a missing key at request time; Ollama
itself ignores the header.

When to prefer which:

- **Native `OllamaProvider`** (this example's default path): keeps the
  admin code path and provider id (`ollama`) honest, and surfaces
  Ollama-specific counters (`prompt_eval_count`, `eval_count`,
  `total_duration`, …) in `AiResponse.usage`.
- **`OpenAiProvider` + Ollama `/v1`**: useful when the same deployment
  must target both a SaaS OpenAI-style provider and a self-hosted
  Ollama via a single code path, or when you want to reuse OpenAI
  request/response handling.

## Files in this example

| File | Purpose |
| --- | --- |
| [`application.properties`](application.properties) | Drop-in `searchable.ai.*` block for `searchable-admin`. |
| [`docker-compose.yml`](docker-compose.yml) | Optional containerised Ollama server. |
